package com.nci.backend.hazard.service;

import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.HazardType;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class NearbyHazardService {
    private final List<HazardProvider> providers;
    private final GeoDistance distance;
    private final Clock clock;

    public NearbyHazardService(List<HazardProvider> providers, GeoDistance distance, Clock clock) {
        this.providers = List.copyOf(providers);
        this.distance = distance;
        this.clock = clock;
    }

    /** Existing scientific/monitoring contracts remain restricted to USGS until their own milestones. */
    public NearbyHazardResponse earthquakes(double latitude, double longitude, double radiusKm, int days) {
        var result = nearby(latitude, longitude, radiusKm, days, HazardType.EARTHQUAKE, "USGS");
        if (result.coverageStatus() == CoverageStatus.UNAVAILABLE) {
            throw new ProviderException(result.providerStatuses().stream()
                    .anyMatch(s -> s.limitations().contains("PROVIDER_TIMEOUT")));
        }
        return result;
    }

    public NearbyHazardResponse nearby(double latitude, double longitude, double radiusKm, int days) {
        return nearby(latitude, longitude, radiusKm, days, null, null);
    }

    public NearbyHazardResponse nearby(double latitude, double longitude, double radiusKm, int days,
                                       HazardType hazardType, String source) {
        if (days < 1 || days > 30) throw new IllegalArgumentException("Invalid lookback");
        if (source != null && providers.stream().noneMatch(p -> source.equals(p.source())))
            throw new IllegalArgumentException("Unsupported provider");
        var now = clock.instant();
        var query = new HazardQuery(latitude, longitude, radiusKm, now.minus(days, ChronoUnit.DAYS), now);
        var selected = providers.stream().filter(p -> source == null || source.equals(p.source()))
                .filter(p -> hazardType == null || p.supportedHazards().contains(hazardType)).toList();
        var events = new LinkedHashMap<String, NormalizedHazardEvent>();
        var statuses = new ArrayList<ProviderStatus>();
        boolean truncated = false;
        for (var provider : selected) {
            try {
                var result = provider.fetch(query);
                truncated |= result.truncated();
                statuses.add(new ProviderStatus(provider.source(), result.coverageStatus(),
                        result.coverageStatus() == CoverageStatus.UNAVAILABLE ? null : clock.instant(), result.limitations()));
                for (var event : result.events()) {
                    // Identity is source + source event ID. Never merge across sources by proximity.
                    String key = event.source() + ":" + event.externalId();
                    events.merge(key, event, NearbyHazardService::latest);
                }
            } catch (RuntimeException failure) {
                // A failed/malformed source cannot erase successful source evidence or expose upstream details.
                statuses.add(new ProviderStatus(provider.source(), CoverageStatus.UNAVAILABLE, null,
                        List.of(failure instanceof ProviderException p && p.getStatusCode().value() == 504
                                ? "PROVIDER_TIMEOUT" : "PROVIDER_UNAVAILABLE")));
            }
        }
        var items = new ArrayList<NearbyHazardResponse.Item>();
        for (var event : events.values()) {
            double km = distance.kilometers(latitude, longitude, event.latitude(), event.longitude());
            // Include interval events overlapping the window, not only disasters that started in it.
            var last = event.endedAt() == null ? event.startedAt() : event.endedAt();
            if (km <= radiusKm && !last.isBefore(query.from()) && !event.startedAt().isAfter(now)
                    && (hazardType == null || event.hazardType() == hazardType))
                items.add(new NearbyHazardResponse.Item(event, km));
        }
        items.sort(Comparator.comparingDouble(NearbyHazardResponse.Item::distanceKm)
                .thenComparing(i -> i.event().source()).thenComparing(i -> i.event().externalId()));
        var coverage = aggregate(statuses);
        return new NearbyHazardResponse(query, clock.instant(), items, truncated,
                selected.stream().map(HazardProvider::metadata).toList(),
                "Selected provider query coverage: " + coverage + ". Point proximity only; affected areas are not intersected. "
                + "Coverage is not a complete inventory of real-world hazards. Empty results do not establish safety. Results are not persisted.",
                coverage, statuses);
    }

    private static NormalizedHazardEvent latest(NormalizedHazardEvent old, NormalizedHazardEvent next) {
        var a = old.sourceUpdatedAt(); var b = next.sourceUpdatedAt();
        return b != null && (a == null || b.isAfter(a)) ? next : old;
    }

    private static CoverageStatus aggregate(List<ProviderStatus> statuses) {
        if (statuses.isEmpty()) return CoverageStatus.UNKNOWN;
        if (statuses.stream().allMatch(s -> s.coverageStatus() == CoverageStatus.UNAVAILABLE)) return CoverageStatus.UNAVAILABLE;
        if (statuses.stream().allMatch(s -> s.coverageStatus() == CoverageStatus.COMPLETE)) return CoverageStatus.COMPLETE;
        if (statuses.stream().anyMatch(s -> s.coverageStatus() == CoverageStatus.UNAVAILABLE
                || s.coverageStatus() == CoverageStatus.PARTIAL || s.coverageStatus() == CoverageStatus.UNKNOWN)) return CoverageStatus.PARTIAL;
        return CoverageStatus.TRUNCATED;
    }
}
