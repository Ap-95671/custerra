package com.nci.backend.risk.service;

import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.*;
import com.nci.backend.hazard.service.GeoDistance;
import com.nci.backend.risk.dto.*;
import com.nci.backend.risk.model.RiskLevel;
import com.nci.backend.weather.dto.WeatherResponse;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** Experimental detection-proximity index; deliberately not wildfire probability, spread or susceptibility. */
@Component
public class ThermalActivityStrategy implements RiskScoringStrategy {
    public static final String VERSION="thermal-proximity-v1-experimental";
    @Override public HazardType hazardType() { return HazardType.WILDFIRE; }
    @Override public Set<String> evidenceSources() { return Set.of("FIRMS"); }
    @Override public HazardRiskResponse assess(NearbyHazardResponse snapshot) { return assess(snapshot,null); }
    @Override public HazardRiskResponse assess(NearbyHazardResponse snapshot,WeatherResponse weather) {
        var factors=new ArrayList<RiskFactorResponse>();
        var limits=new ArrayList<>(List.of("THERMAL_ANOMALIES_NOT_CONFIRMED_WILDFIRES","UNVALIDATED_PROXIMITY_HEURISTIC",
                "NO_FUEL_TERRAIN_OR_SPREAD_MODEL","GEOGRAPHICAL_RELEVANCE_NOT_ASSESSED","SINGLE_SENSOR_OVERPASS_COVERAGE"));
        boolean incomplete=snapshot.truncated() || snapshot.coverageStatus()!=com.nci.backend.provider.model.CoverageStatus.COMPLETE;
        if(incomplete) limits.add("INCOMPLETE_FIRMS_QUERY");
        for(var item:snapshot.items()) {
            var e=item.event();
            if(!"FIRMS".equals(e.source()) || e.hazardType()!=HazardType.WILDFIRE || e.informationType()!=InformationType.OBSERVED_EVENT) continue;
            if(!Double.isFinite(item.distanceKm()) || item.distanceKm()<0 || e.startedAt()==null) { incomplete=true;continue; }
            if(item.distanceKm()>snapshot.query().radiusKm() || e.startedAt().isBefore(snapshot.query().from()) || e.startedAt().isAfter(snapshot.query().to())) continue;
            double hours=Duration.between(e.startedAt(),snapshot.query().to()).toMillis()/3_600_000.;
            boolean usable=e.confidence()==ConfidenceLevel.MEDIUM || e.confidence()==ConfidenceLevel.HIGH;
            if(!usable) { incomplete=true;if(!limits.contains("LOW_OR_UNKNOWN_DETECTION_CONFIDENCE"))limits.add("LOW_OR_UNKNOWN_DETECTION_CONFIDENCE"); }
            // Explicit ordinal proximity/recency index. No summing overlapping satellite pixels.
            int proximity=item.distanceKm()<=10?60:item.distanceKm()<=25?40:20;
            double recency=hours<=24?1:hours<=72?.5:.25;
            Integer contribution=usable?(int)Math.round(proximity*recency):null;
            factors.add(new RiskFactorResponse("THERMAL_PROXIMITY","Proximity index (≤10 km:60, ≤25 km:40, otherwise:20) × age weight (≤24 h:1, ≤72 h:0.5, otherwise:0.25). Only nominal/high-confidence detections are scorable.",
                    e.source(),e.externalId(),e.sourceUrl(),e.startedAt(),e.sourceUpdatedAt(),item.distanceKm(),null,hours,null,null,recency,contribution));
        }
        factors.sort(Comparator.comparing(RiskFactorResponse::externalId));
        if(factors.isEmpty()) { incomplete=true;limits.add("NO_SCORABLE_OBSERVATIONS"); }
        Integer score=incomplete?null:factors.stream().mapToInt(RiskFactorResponse::contribution).max().orElseThrow();
        var environment=new ArrayList<HazardRiskResponse.EnvironmentalFactor>();
        boolean validWeather=weather!=null && !weather.stale() && !weather.validAt().isAfter(snapshot.query().to())
                && !weather.validAt().isBefore(snapshot.query().to().minus(Duration.ofHours(2)))
                && new GeoDistance().kilometers(snapshot.query().latitude(),snapshot.query().longitude(),weather.gridLatitude(),weather.gridLongitude())<=50;
        if(validWeather && weather.windSpeedKmh()!=null && Double.isFinite(weather.windSpeedKmh()) && weather.windSpeedKmh()>=0) {
            environment.add(new HazardRiskResponse.EnvironmentalFactor("MODELED_WIND_CONTEXT",weather.windSpeedKmh(),"km/h",weather.source().id(),weather.validAt(),weather.fetchedAt(),
                    "Wind can affect fire behavior, but this point model lacks fuels, terrain and fire confirmation. Context only; no score change or spread inference."));
        } else limits.add("CURRENT_LOCAL_WIND_CONTEXT_UNAVAILABLE");
        return new HazardRiskResponse(hazardType(),"RECENT_THERMAL_DETECTION_PROXIMITY",VERSION,score==null?RiskLevel.UNKNOWN:
                score<25?RiskLevel.LOW:score<50?RiskLevel.MODERATE:RiskLevel.HIGH,score,score==null?ConfidenceLevel.UNKNOWN:ConfidenceLevel.LOW,
                "Experimental proximity to satellite thermal detections, not wildfire risk or a prediction. "
                + (score==null?"Evidence is insufficient for a complete indicator; UNKNOWN is not safety.":"The largest single detection contribution defines this index; repeated pixels are not summed."),
                factors,limits,snapshot.items(),environment);
    }
}
