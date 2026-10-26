package com.nci.backend.hazard;

import com.nci.backend.hazard.entity.HazardEvent;
import com.nci.backend.hazard.model.*;
import java.time.Instant;

final class HazardFixtures {
    static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    static HazardEvent event(String externalId, HazardType type, SeverityLevel severity, Instant start) {
        return new HazardEvent(externalId, type, "Synthetic test fixture", "Test-only event",
                severity, InformationType.OBSERVED_EVENT, 32.24, 77.19, null, start, null,
                "test-source", "https://example.test/events/" + externalId, start.plusSeconds(60),
                ConfidenceLevel.UNKNOWN);
    }
}
