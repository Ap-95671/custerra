package com.nci.backend.risk.dto;

import com.nci.backend.hazard.model.ConfidenceLevel;
import com.nci.backend.hazard.model.HazardType;
import com.nci.backend.risk.model.RiskLevel;
import java.util.List;

public record HazardRiskResponse(HazardType hazardType, String scope, String modelVersion,
        RiskLevel riskLevel, Integer score, ConfidenceLevel confidence,
        String explanation, List<RiskFactorResponse> factors, List<String> limitations,
        List<com.nci.backend.hazard.dto.NearbyHazardResponse.Item> evidence,
        List<EnvironmentalFactor> environmentalContext) {
    public HazardRiskResponse { factors = List.copyOf(factors); limitations = List.copyOf(limitations); evidence=List.copyOf(evidence); environmentalContext=List.copyOf(environmentalContext); }
    public HazardRiskResponse(HazardType hazardType,String scope,String modelVersion,RiskLevel riskLevel,Integer score,
            ConfidenceLevel confidence,String explanation,List<RiskFactorResponse> factors,List<String> limitations) {
        this(hazardType,scope,modelVersion,riskLevel,score,confidence,explanation,factors,limitations,List.of(),List.of());
    }
    public record EnvironmentalFactor(String code,Double value,String unit,String source,java.time.Instant validAt,
            java.time.Instant retrievedAt,String explanation) {}
}

