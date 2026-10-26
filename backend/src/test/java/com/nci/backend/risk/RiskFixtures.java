package com.nci.backend.risk;

import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.*;
import com.nci.backend.provider.model.*;
import java.time.*;
import java.util.*;

final class RiskFixtures {
    static final Instant NOW = Instant.parse("2026-01-31T00:00:00Z");

    static NearbyHazardResponse snapshot(boolean truncated, NearbyHazardResponse.Item... items) {
        return new NearbyHazardResponse(new HazardQuery(0, 0, 1000, NOW.minus(Duration.ofDays(30)), NOW),
                NOW, List.of(items), truncated,
                List.of(new ProviderMetadata("TEST", "Test-only source", "https://example.test",
                        "https://example.test/license", "Test snapshot")), "Test earthquake observations");
    }

    static NearbyHazardResponse.Item event(String id, Double magnitude, double km, Duration age) {
        return new NearbyHazardResponse.Item(new NormalizedHazardEvent(id, HazardType.EARTHQUAKE,
                "Synthetic test observation", SeverityLevel.UNKNOWN, InformationType.OBSERVED_EVENT,
                0, 0, null, NOW.minus(age), null, "TEST", "https://example.test/" + id,
                NOW, NOW.minus(age).plusSeconds(1), ConfidenceLevel.UNKNOWN,
                magnitude == null ? Map.of() : Map.of("magnitude", magnitude)), km);
    }
}
