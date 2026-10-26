package com.nci.backend.provider.model;

import java.time.Instant;
import java.util.List;

public record ProviderStatus(String provider, CoverageStatus coverageStatus, Instant retrievedAt,
                             List<String> limitations) {
    public ProviderStatus { limitations = List.copyOf(limitations); }
}
