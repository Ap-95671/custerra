package com.nci.backend.risk.service;

import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.HazardType;
import com.nci.backend.risk.dto.HazardRiskResponse;

/** Pure assessment of a supplied evidence snapshot; implementations must not fetch data. */
public interface RiskScoringStrategy {
    HazardType hazardType();
    default java.util.Set<String> evidenceSources() { return java.util.Set.of("USGS"); }
    default HazardRiskResponse assess(NearbyHazardResponse snapshot, com.nci.backend.weather.dto.WeatherResponse weather) {
        return assess(snapshot);
    }
    HazardRiskResponse assess(NearbyHazardResponse snapshot);
}
