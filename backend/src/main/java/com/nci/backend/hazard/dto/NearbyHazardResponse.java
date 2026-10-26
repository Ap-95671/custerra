package com.nci.backend.hazard.dto;

import com.nci.backend.provider.model.HazardQuery;
import com.nci.backend.provider.model.NormalizedHazardEvent;
import java.time.Instant;
import java.util.List;

public record NearbyHazardResponse(HazardQuery query, Instant fetchedAt, List<Item> items,
        boolean truncated, List<com.nci.backend.provider.model.ProviderMetadata> sources, String coverage,
        com.nci.backend.provider.model.CoverageStatus coverageStatus,
        List<com.nci.backend.provider.model.ProviderStatus> providerStatuses) {
    public NearbyHazardResponse { items = List.copyOf(items); sources = List.copyOf(sources); providerStatuses = List.copyOf(providerStatuses); }
    public NearbyHazardResponse(HazardQuery query, Instant fetchedAt, List<Item> items, boolean truncated,
            List<com.nci.backend.provider.model.ProviderMetadata> sources, String coverage) {
        this(query, fetchedAt, items, truncated, sources, coverage,
                truncated ? com.nci.backend.provider.model.CoverageStatus.TRUNCATED : com.nci.backend.provider.model.CoverageStatus.COMPLETE,
                List.of());
    }
    public record Item(NormalizedHazardEvent event, double distanceKm) {}
}
