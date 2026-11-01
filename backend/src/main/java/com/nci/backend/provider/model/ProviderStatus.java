package com.nci.backend.provider.model;

import java.time.Instant;
import java.util.List;

public record ProviderStatus(String provider, CoverageStatus coverageStatus, Instant retrievedAt,
                             List<String> limitations) {
    public ProviderStatus { limitations = List.copyOf(limitations); }
    @com.fasterxml.jackson.annotation.JsonProperty("freshness")
    public String freshness() {
        if(retrievedAt==null)return "UNKNOWN";
        return limitations.stream().anyMatch(note->note.startsWith("STALE_"))?"STALE":"RETRIEVAL_TIMESTAMP_AVAILABLE";
    }
    @com.fasterxml.jackson.annotation.JsonProperty("availability")
    public String availability() {
        if(limitations.stream().anyMatch(note->note.startsWith("NOT_CONFIGURED")))return "NOT_CONFIGURED";
        return coverageStatus==CoverageStatus.UNAVAILABLE?"UNAVAILABLE":coverageStatus==CoverageStatus.COMPLETE?"AVAILABLE":"DEGRADED";
    }
}
