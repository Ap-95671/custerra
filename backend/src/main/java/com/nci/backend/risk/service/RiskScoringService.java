package com.nci.backend.risk.service;

import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.*;
import com.nci.backend.risk.dto.HazardRiskResponse;
import com.nci.backend.risk.model.RiskLevel;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RiskScoringService {
    private final Map<HazardType, RiskScoringStrategy> strategies;

    public RiskScoringService(List<RiskScoringStrategy> strategies) {
        var byHazard = new EnumMap<HazardType, RiskScoringStrategy>(HazardType.class);
        for (var strategy : strategies) {
            if (byHazard.putIfAbsent(strategy.hazardType(), strategy) != null) {
                throw new IllegalArgumentException("Duplicate risk strategy for " + strategy.hazardType());
            }
        }
        this.strategies = Map.copyOf(byHazard);
    }

    public List<HazardRiskResponse> assess(NearbyHazardResponse snapshot) {
        return assess(snapshot,null);
    }
    public List<HazardRiskResponse> assess(NearbyHazardResponse snapshot, com.nci.backend.weather.dto.WeatherResponse weather) {
        return Arrays.stream(HazardType.values()).map(type -> {
            var strategy = strategies.get(type);
            if (strategy != null) {
                var scoped=scope(snapshot,strategy.evidenceSources());
                var result=strategy.assess(scoped,weather);
                return new HazardRiskResponse(result.hazardType(),result.scope(),result.modelVersion(),result.riskLevel(),result.score(),
                        result.confidence(),result.explanation(),result.factors(),result.limitations(),scoped.items(),result.environmentalContext());
            }
            return new HazardRiskResponse(type, "NOT_ASSESSED", null, RiskLevel.UNKNOWN, null,
                    ConfidenceLevel.UNKNOWN,
                    "No assessment model or sufficient data is connected for this hazard; geographical relevance is unknown.",
                    List.of(), List.of("UNSUPPORTED_HAZARD", "GEOGRAPHICAL_RELEVANCE_NOT_ASSESSED"));
        }).toList();
    }
    private NearbyHazardResponse scope(NearbyHazardResponse snapshot,Set<String> sources) {
        // Old persisted/test snapshots without per-provider status retain their original completeness semantics.
        if(snapshot.providerStatuses().isEmpty()) return snapshot;
        var statuses=snapshot.providerStatuses().stream().filter(s->sources.contains(s.provider())).toList();
        boolean incomplete=statuses.isEmpty() || statuses.stream().anyMatch(s->s.coverageStatus()!=com.nci.backend.provider.model.CoverageStatus.COMPLETE);
        return new NearbyHazardResponse(snapshot.query(),snapshot.fetchedAt(),snapshot.items().stream().filter(i->sources.contains(i.event().source())).toList(),
                statuses.stream().anyMatch(s->s.coverageStatus()==com.nci.backend.provider.model.CoverageStatus.TRUNCATED),snapshot.sources().stream().filter(s->sources.contains(s.id())).toList(),"Strategy-specific source coverage",
                incomplete?com.nci.backend.provider.model.CoverageStatus.PARTIAL:com.nci.backend.provider.model.CoverageStatus.COMPLETE,statuses);
    }
}

