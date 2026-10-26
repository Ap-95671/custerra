package com.nci.backend.risk.dto;

import java.time.Instant;
import java.util.List;
import com.nci.backend.hazard.model.*;
import com.nci.backend.risk.model.RiskLevel;
import com.nci.backend.provider.model.*;

public record MultiHazardRiskResponse(String modelVersion,InformationType informationType,HazardQuery query,Instant assessedAt,
        RiskLevel overallRisk,Integer score,ConfidenceLevel confidence,String explanation,List<HazardRiskResponse> hazards,
        List<ProviderMetadata> dataSources,boolean sourceResultsTruncated,List<String> limitations,String safetyNotice,
        List<HazardType> dominantHazards,int assessedHazardCount,int unassessedHazardCount,CoverageStatus coverageStatus,
        List<ProviderStatus> providerStatuses,String weatherStatus) {
    public MultiHazardRiskResponse {
        hazards=List.copyOf(hazards);dataSources=List.copyOf(dataSources);limitations=List.copyOf(limitations);
        dominantHazards=List.copyOf(dominantHazards);providerStatuses=List.copyOf(providerStatuses);
    }
}
