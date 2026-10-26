package com.nci.backend.monitoring.dto;
import java.time.Instant;
import java.util.UUID;
import com.nci.backend.route.dto.RouteRiskResponse;
public record JourneyNotice(UUID id, UUID journeyId, Instant createdAt, String type, RouteRiskResponse.Match evidence,
        String explanation, long stateVersion) {
    public JourneyNotice(UUID id, UUID journeyId, Instant createdAt, String type, RouteRiskResponse.Match evidence, String explanation) {
        this(id,journeyId,createdAt,type,evidence,explanation,0);
    }
}
