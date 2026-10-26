package com.nci.backend.risk.service;

import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.*;
import com.nci.backend.risk.dto.*;
import com.nci.backend.risk.model.RiskLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EarthquakeActivityStrategy implements RiskScoringStrategy {
    public static final String VERSION = "earthquake-activity-v1-experimental";
    private static final String SCOPE = "RECENT_EARTHQUAKE_ACTIVITY";

    @Override
    public HazardType hazardType() { return HazardType.EARTHQUAKE; }

    @Override
    public HazardRiskResponse assess(NearbyHazardResponse snapshot) {
        var factors = new ArrayList<RiskFactorResponse>();
        var limitations = new ArrayList<>(List.of(
                "UNVALIDATED_HEURISTIC", "NO_HISTORICAL_BASELINE", "NO_GROUND_MOTION_OR_LOCAL_VULNERABILITY"));
        if (snapshot.truncated()) limitations.add("TRUNCATED_SOURCE_RESULTS");
        if (snapshot.coverageStatus()!=com.nci.backend.provider.model.CoverageStatus.COMPLETE) limitations.add("INCOMPLETE_SOURCE_QUERY");
        for (var item : snapshot.items()) {
            var event = item.event();
            if (event.hazardType() != HazardType.EARTHQUAKE
                    || event.informationType() != InformationType.OBSERVED_EVENT) continue;
            if (event.startedAt() == null || !Double.isFinite(item.distanceKm()) || item.distanceKm() < 0) {
                if (!limitations.contains("INVALID_EVIDENCE")) limitations.add("INVALID_EVIDENCE");
                continue;
            }
            if (item.distanceKm() > snapshot.query().radiusKm() || event.startedAt().isBefore(snapshot.query().from())
                    || event.startedAt().isAfter(snapshot.query().to())) continue;
            Duration age = Duration.between(event.startedAt(), snapshot.query().to());
            Double magnitude = event.measurements().get("magnitude");
            boolean usable = magnitude != null && Double.isFinite(magnitude);
            Integer base = usable ? magnitudePoints(magnitude) : null;
            double distanceWeight = distanceWeight(item.distanceKm());
            double recencyWeight = recencyWeight(age);
            Integer contribution = usable ? (int) Math.round(base * distanceWeight * recencyWeight) : null;
            if (!usable && !limitations.contains("MISSING_OR_INVALID_MAGNITUDE")) {
                limitations.add("MISSING_OR_INVALID_MAGNITUDE");
            }
            factors.add(new RiskFactorResponse(usable ? "RECENT_EARTHQUAKE" : "MISSING_OR_INVALID_MAGNITUDE",
                    usable ? "Contribution = round(magnitude points × distance weight × recency weight)."
                            : "This observation has no usable magnitude; a complete indicator cannot be assigned.",
                    event.source(), event.externalId(), event.sourceUrl(), event.startedAt(), event.sourceUpdatedAt(),
                    item.distanceKm(), usable ? magnitude : null, age.toMillis() / 3_600_000.0,
                    base, distanceWeight, recencyWeight, contribution));
        }
        factors.sort(Comparator.comparing(RiskFactorResponse::source).thenComparing(RiskFactorResponse::externalId));
        if (factors.isEmpty()) limitations.add("NO_SCORABLE_OBSERVATIONS");
        boolean incomplete = snapshot.truncated() || snapshot.coverageStatus()!=com.nci.backend.provider.model.CoverageStatus.COMPLETE || factors.isEmpty()
                || limitations.contains("MISSING_OR_INVALID_MAGNITUDE") || limitations.contains("INVALID_EVIDENCE");
        Integer score = incomplete ? null : factors.stream().mapToInt(RiskFactorResponse::contribution).max().orElseThrow();
        return new HazardRiskResponse(hazardType(), SCOPE, VERSION,
                score == null ? RiskLevel.UNKNOWN : level(score), score,
                score == null ? ConfidenceLevel.UNKNOWN : ConfidenceLevel.LOW,
                score == null ? "Insufficient complete observations to assign a recent-earthquake activity indicator. "
                        + "Available evidence contributions remain visible; unknown does not mean safe."
                        : "The score is the largest single-event contribution in the query window. "
                        + "The level describes recent earthquake activity only, not future earthquake probability or travel safety.",
                factors, limitations);
    }

    private int magnitudePoints(double magnitude) {
        if (magnitude >= 7) return 100;
        if (magnitude >= 6) return 80;
        if (magnitude >= 5) return 60;
        if (magnitude >= 4) return 40;
        return 20;
    }

    private double distanceWeight(double km) {
        if (km <= 25) return 1;
        if (km <= 100) return 0.75;
        if (km <= 250) return 0.5;
        return 0.25;
    }

    private double recencyWeight(Duration age) {
        if (age.compareTo(Duration.ofHours(24)) <= 0) return 1;
        if (age.compareTo(Duration.ofHours(72)) <= 0) return 0.75;
        if (age.compareTo(Duration.ofDays(7)) <= 0) return 0.5;
        return 0.25;
    }

    private RiskLevel level(int score) {
        if (score < 25) return RiskLevel.LOW;
        if (score < 50) return RiskLevel.MODERATE;
        return RiskLevel.HIGH;
    }
}
