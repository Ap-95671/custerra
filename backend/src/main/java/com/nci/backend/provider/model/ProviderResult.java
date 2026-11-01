package com.nci.backend.provider.model;

import java.util.List;

public record ProviderResult(List<NormalizedHazardEvent> events, boolean truncated,
                             CoverageStatus coverageStatus, List<String> limitations, java.time.Instant retrievedAt) {
    public ProviderResult(List<NormalizedHazardEvent> events,boolean truncated,CoverageStatus coverageStatus,List<String> limitations) {
        this(events,truncated,coverageStatus,limitations,null);
    }
    public ProviderResult { events = List.copyOf(events); limitations = List.copyOf(limitations); }
    public ProviderResult(List<NormalizedHazardEvent> events, boolean truncated) {
        this(events, truncated, truncated ? CoverageStatus.TRUNCATED : CoverageStatus.COMPLETE, List.of());
    }
}
