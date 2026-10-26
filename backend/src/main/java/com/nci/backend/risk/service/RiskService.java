package com.nci.backend.risk.service;

import com.nci.backend.hazard.model.*;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.risk.dto.RiskResponse;
import com.nci.backend.risk.model.RiskLevel;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RiskService {
    private final NearbyHazardService nearby;
    private final RiskScoringService scoring;

    public RiskService(NearbyHazardService nearby, RiskScoringService scoring) {
        this.nearby = nearby;
        this.scoring = scoring;
    }

    public RiskResponse assess(double latitude, double longitude, double radiusKm, int days) {
        var snapshot = nearby.earthquakes(latitude, longitude, radiusKm, days);
        return new RiskResponse("location-risk-v1-experimental", InformationType.MODELED_RISK,
                snapshot.query(), snapshot.fetchedAt(), RiskLevel.UNKNOWN, null, ConfidenceLevel.UNKNOWN,
                "Overall location risk cannot be established from recent earthquake observations alone. "
                        + "See the scoped earthquake activity assessment and its evidence.",
                scoring.assess(snapshot), snapshot.sources(), snapshot.truncated(),
                List.of("EARTHQUAKE_OBSERVATIONS_ONLY", "UNVALIDATED_MODEL", "NO_HISTORICAL_BASELINE",
                        "NO_WEATHER_TERRAIN_OR_EXPOSURE_DATA", "NOT_A_PROBABILITY_OR_FORECAST"),
                "Custerra is not an official emergency-warning authority. Follow local government and emergency instructions. "
                        + "Unknown or low activity does not establish travel safety. This model does not predict earthquakes.");
    }
}
