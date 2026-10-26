package com.nci.backend.risk.service;

import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.hazard.model.*;
import com.nci.backend.risk.dto.*;
import com.nci.backend.risk.model.RiskLevel;
import com.nci.backend.weather.service.WeatherService;
import com.nci.backend.weather.dto.WeatherResponse;
import com.nci.backend.provider.ProviderException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MultiHazardRiskService {
    private final NearbyHazardService nearby; private final RiskScoringService scoring; private final WeatherService weather;
    public MultiHazardRiskService(NearbyHazardService nearby,RiskScoringService scoring,WeatherService weather) {
        this.nearby=nearby;this.scoring=scoring;this.weather=weather;
    }
    public MultiHazardRiskResponse assess(double latitude,double longitude,double radiusKm,int days) {
        var snapshot=nearby.nearby(latitude,longitude,radiusKm,days);
        WeatherResponse context=null;String weatherStatus="AVAILABLE";
        try { context=weather.location(latitude,longitude); weatherStatus=context==null?"UNAVAILABLE":context.stale()?"STALE":context.partial()?"PARTIAL":"AVAILABLE"; }
        catch(ProviderException e) { weatherStatus="UNAVAILABLE"; }
        var hazards=scoring.assess(snapshot,context);
        var assessed=hazards.stream().filter(h->h.riskLevel()!=RiskLevel.UNKNOWN).toList();
        var dominant=dominant(assessed);
        var sources=new ArrayList<>(snapshot.sources());if(context!=null)sources.add(context.source());
        return new MultiHazardRiskResponse("overall-risk-v2-experimental",InformationType.MODELED_RISK,snapshot.query(),snapshot.fetchedAt(),
                RiskLevel.UNKNOWN,null,ConfidenceLevel.UNKNOWN,
                "Overall risk is UNKNOWN: hazard-specific activity indices are uncalibrated and cannot establish all-hazard risk. "
                + "Dominant hazards identify the highest available indicator category; scores are never averaged and unknowns are never zero.",
                hazards,sources,snapshot.truncated(),List.of("NO_CALIBRATED_ALL_HAZARD_AGGREGATION","UNSUPPORTED_HAZARDS_REMAIN_UNKNOWN",
                "GEOGRAPHICAL_RELEVANCE_NOT_ASSESSED","NO_EARTHQUAKE_PREDICTION"),
                "Custerra is experimental, not an official emergency-warning authority. Follow competent local authorities; unknown or low activity does not establish safety.",
                dominant,assessed.size(),hazards.size()-assessed.size(),snapshot.coverageStatus(),snapshot.providerStatuses(),weatherStatus);
    }
    static List<HazardType> dominant(List<HazardRiskResponse> assessed) {
        int rank=assessed.stream().mapToInt(h->rank(h.riskLevel())).max().orElse(0);
        return assessed.stream().filter(h->rank(h.riskLevel())==rank).map(HazardRiskResponse::hazardType).toList();
    }
    private static int rank(RiskLevel level) { return switch(level) { case HIGH->3;case MODERATE->2;case LOW->1;case UNKNOWN->0; }; }
}
