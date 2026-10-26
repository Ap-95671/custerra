package com.nci.backend.risk.dto;

import com.nci.backend.hazard.model.ConfidenceLevel;
import com.nci.backend.hazard.model.InformationType;
import com.nci.backend.provider.model.HazardQuery;
import com.nci.backend.provider.model.ProviderMetadata;
import com.nci.backend.risk.model.RiskLevel;
import java.time.Instant;
import java.util.List;

public record RiskResponse(String modelVersion, InformationType informationType, HazardQuery query,
        Instant assessedAt, RiskLevel overallRisk, Integer score, ConfidenceLevel confidence,
        String explanation, List<HazardRiskResponse> hazards, List<ProviderMetadata> dataSources,
        boolean sourceResultsTruncated, List<String> limitations, String safetyNotice) {
    public RiskResponse {
        hazards = List.copyOf(hazards);
        dataSources = List.copyOf(dataSources);
        limitations = List.copyOf(limitations);
    }
}
