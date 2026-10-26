package com.nci.backend.history;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.nci.backend.hazard.service.GeoDistance;
import com.nci.backend.hazard.model.HazardType;
@Service
public class HistoryService {
    private final HistoricalProvider provider;
    private final HistoryRepository repository;
    private final GeoDistance distance;
    private final Clock clock;
    public HistoryService(HistoricalProvider provider, HistoryRepository repository, GeoDistance distance, Clock clock) {
        this.provider = provider; this.repository = repository; this.distance = distance; this.clock = clock;
    }
    public HistorySnapshot create(HistoryQuery query) {
        query.validate(clock.instant());
        var result = provider.fetchHistory(query);
        if (result.events().stream().anyMatch(e -> e.hazardType() == HazardType.EARTHQUAKE
                && (!e.measurements().containsKey("magnitude") || !Double.isFinite(e.measurements().get("magnitude")))))
            throw new com.nci.backend.provider.ProviderException(false);
        var events = result.events().stream().filter(e -> e.hazardType() == HazardType.EARTHQUAKE
                && !e.startedAt().isBefore(query.from()) && e.startedAt().isBefore(query.to())
                && e.measurements().getOrDefault("magnitude", Double.NEGATIVE_INFINITY) >= query.minMagnitude()
                && distance.kilometers(query.latitude(), query.longitude(), e.latitude(), e.longitude()) <= query.radiusKm())
                .sorted(Comparator.comparing(com.nci.backend.provider.model.NormalizedHazardEvent::startedAt)
                        .thenComparing(com.nci.backend.provider.model.NormalizedHazardEvent::externalId)).toList();
        double years = Duration.between(query.from(), query.to()).toMillis() / (86400000.0 * 365.2425);
        var snapshot = new HistorySnapshot(UUID.randomUUID(), "catalog-frequency-v1", query, clock.instant(),
                events.size(), years, result.truncated() ? null : events.size() / years, result.truncated(), events,
                provider.metadata(), "Observed catalog frequency = matching event count / (window days / 365.2425). "
                + "Window includes from and excludes to. Truncated counts are lower bounds; frequency is withheld. "
                + "Catalog completeness varies by region, era and magnitude; zero records do not establish safety. "
                + "This is historical exposure, not a forecast, recurrence probability or calibrated risk score.");
        repository.save(snapshot);
        return snapshot;
    }
    public HistorySnapshot get(UUID id) {
        return repository.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
