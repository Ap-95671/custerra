package com.nci.backend.route.dto;

import java.time.Instant;
import java.util.List;
import com.nci.backend.provider.model.ProviderMetadata;

public record PlannedRouteResponse(RoutePlanRequest query, String geometry, int geometryPrecision,
        double distanceKm, double durationSeconds, Bounds bounds, int segmentCount,
        ProviderMetadata source, Instant retrievedAt, Exposure assessment, String assessmentError, List<String> limitations) {
    public PlannedRouteResponse { limitations=List.copyOf(limitations); }
    public record Bounds(double south, double west, double north, double east) {}
    public record Exposure(String modelVersion,Instant assessedAt,String overallRisk,boolean truncated,
            List<RouteRiskResponse.Match> matches,List<ProviderMetadata> sources,String explanation) {
        public Exposure { matches=List.copyOf(matches);sources=List.copyOf(sources); }
    }
}
