package com.nci.backend.route.dto;
import java.time.Instant;
import java.util.List;
import com.nci.backend.provider.model.*;
public record RouteRiskResponse(String modelVersion, RouteRiskRequest query, Instant assessedAt,
        double lengthKm, String overallRisk, boolean truncated, List<Match> matches,
        List<ProviderMetadata> sources, String explanation, CoverageStatus coverageStatus, List<ProviderStatus> providerStatuses) {
    public RouteRiskResponse { matches = List.copyOf(matches); sources = List.copyOf(sources);
        coverageStatus = coverageStatus == null ? (truncated ? CoverageStatus.TRUNCATED : CoverageStatus.COMPLETE) : coverageStatus;
        providerStatuses = providerStatuses == null ? List.of() : List.copyOf(providerStatuses); }
    public RouteRiskResponse(String modelVersion, RouteRiskRequest query, Instant assessedAt, double lengthKm, String overallRisk,
            boolean truncated, List<Match> matches, List<ProviderMetadata> sources, String explanation) {
        this(modelVersion, query, assessedAt, lengthKm, overallRisk, truncated, matches, sources, explanation, null, List.of());
    }
    public record Match(NormalizedHazardEvent event, double distanceKm, int segmentIndex) {}
}
