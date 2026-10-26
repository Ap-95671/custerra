package com.nci.backend.provider.model;

import com.nci.backend.hazard.model.*;
import java.time.Instant;
import java.util.Map;

/** Provider-neutral observation; persistence identity is assigned only when stored. */
public record NormalizedHazardEvent(String externalId, HazardType hazardType, String title,
        SeverityLevel severity, InformationType informationType, double latitude, double longitude,
        Double affectedRadiusKm, Instant startedAt, Instant endedAt, String source, String sourceUrl,
        Instant observedAt, Instant sourceUpdatedAt, ConfidenceLevel confidence, Map<String, Double> measurements,
        Instant retrievedAt, Map<String, String> attributes) {
    public NormalizedHazardEvent { measurements = Map.copyOf(measurements); attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        if (retrievedAt == null) retrievedAt = observedAt; }
    /** Compatibility constructor for existing snapshots and earthquake consumers. */
    public NormalizedHazardEvent(String externalId, HazardType hazardType, String title,
            SeverityLevel severity, InformationType informationType, double latitude, double longitude,
            Double affectedRadiusKm, Instant startedAt, Instant endedAt, String source, String sourceUrl,
            Instant observedAt, Instant sourceUpdatedAt, ConfidenceLevel confidence, Map<String, Double> measurements) {
        this(externalId, hazardType, title, severity, informationType, latitude, longitude, affectedRadiusKm,
                startedAt, endedAt, source, sourceUrl, observedAt, sourceUpdatedAt, confidence, measurements,
                observedAt, Map.of());
    }
}
