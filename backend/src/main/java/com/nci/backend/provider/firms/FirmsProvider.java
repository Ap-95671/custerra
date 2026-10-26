package com.nci.backend.provider.firms;

import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import com.nci.backend.hazard.model.HazardType;
import com.nci.backend.hazard.service.GeoDistance;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FirmsProvider implements HazardProvider {
    private final FirmsProperties properties;
    private final FirmsMapper mapper;
    private final Clock clock;
    private final RestClient client;
    @org.springframework.beans.factory.annotation.Autowired
    public FirmsProvider(FirmsProperties properties, FirmsMapper mapper, Clock clock) {
        this(properties, mapper, clock, ProviderHttp.client(properties.connectTimeoutMs(), properties.readTimeoutMs()));
    }
    FirmsProvider(FirmsProperties properties, FirmsMapper mapper, Clock clock, RestClient client) {
        this.properties = properties; this.mapper = mapper; this.clock = clock; this.client = client;
    }
    @Override public String source() { return "FIRMS"; }
    @Override public Set<HazardType> supportedHazards() { return Set.of(HazardType.WILDFIRE); }
    @Override public ProviderMetadata metadata() {
        return new ProviderMetadata(source(), "NASA FIRMS / LANCE, VIIRS Suomi NPP",
                "https://firms.modaps.eosdis.nasa.gov/api/area/", "https://www.earthdata.nasa.gov/engage/open-data-services-software/data-use-policy",
                "On demand, up to five UTC calendar dates; satellite overpass and processing delays apply.");
    }
    @Override public ProviderResult fetch(HazardQuery query) {
        if (properties.mapKey().isBlank()) return new ProviderResult(List.of(), false, CoverageStatus.UNAVAILABLE,
                List.of("NOT_CONFIGURED: NASA FIRMS map key is required."));
        var today = query.to().atZone(ZoneOffset.UTC).toLocalDate();
        var first = query.from().atZone(ZoneOffset.UTC).toLocalDate();
        if (first.isBefore(today.minusDays(4))) first = today.minusDays(4);
        int days = (int) ChronoUnit.DAYS.between(first, today) + 1;
        var notes = new ArrayList<String>(List.of("THERMAL_ANOMALIES_NOT_CONFIRMED_WILDFIRES",
                "SINGLE_VIIRS_PRODUCT: Overpasses, clouds and sensor resolution limit detection."));
        boolean partial = query.from().isBefore(first.atStartOfDay().toInstant(ZoneOffset.UTC));
        if (partial) notes.add("SHORTER_TIME_COVERAGE: Only " + first + " through " + today + " UTC were requested.");
        var events = new LinkedHashMap<String, NormalizedHazardEvent>();
        int rejected = 0;
        var distance = new GeoDistance();
        try {
            for (String box : boxes(query)) {
                // Host and product are fixed so a configuration change cannot send the key to another origin.
                var uri = URI.create("https://firms.modaps.eosdis.nasa.gov/api/area/csv/" + properties.mapKey()
                        + "/VIIRS_SNPP_NRT/" + box + "/" + days + "/" + first);
                var bytes = ProviderHttp.get(client, uri, "text/csv", 2_000_000);
                var retrievedAt = clock.instant();
                for (var row : FirmsCsv.rows(new String(bytes, StandardCharsets.UTF_8))) {
                    try {
                        var event = mapper.map(row, retrievedAt);
                        if (!event.startedAt().isBefore(query.from()) && !event.startedAt().isAfter(query.to())
                                && distance.kilometers(query.latitude(), query.longitude(), event.latitude(), event.longitude()) <= query.radiusKm())
                            events.put(event.externalId(), event);
                    } catch (IllegalArgumentException | DateTimeException e) { rejected++; }
                }
            }
        } catch (ProviderException e) { throw e; }
        catch (RuntimeException e) { throw new ProviderException(false); }
        if (rejected > 0) { partial = true; notes.add("REJECTED_ROWS: " + rejected); }
        boolean truncated = events.size() > 500;
        if (truncated) notes.add("EVENT_LIMIT: newest 500 matching thermal anomalies returned.");
        return new ProviderResult(events.values().stream().sorted(Comparator.comparing(NormalizedHazardEvent::startedAt)
                .reversed().thenComparing(NormalizedHazardEvent::externalId)).limit(500).toList(), truncated,
                partial ? CoverageStatus.PARTIAL : truncated ? CoverageStatus.TRUNCATED : CoverageStatus.COMPLETE, notes);
    }

    /** Spherical circle envelope split at the dateline; pole-reaching queries span every longitude. */
    static List<String> boxes(HazardQuery q) {
        double angle = q.radiusKm() / 6371.0088;
        double delta = Math.toDegrees(angle), south = Math.max(-90, q.latitude() - delta), north = Math.min(90, q.latitude() + delta);
        if (south <= -90 || north >= 90) return List.of(box(-180, south, 180, north));
        double width = Math.toDegrees(Math.asin(Math.sin(angle) / Math.cos(Math.toRadians(q.latitude()))));
        double west = q.longitude() - width, east = q.longitude() + width;
        if (west < -180) return List.of(box(west + 360, south, 180, north), box(-180, south, east, north));
        if (east > 180) return List.of(box(west, south, 180, north), box(-180, south, east - 360, north));
        return List.of(box(west, south, east, north));
    }
    private static String box(double west, double south, double east, double north) {
        return west + "," + south + "," + east + "," + north;
    }
}
