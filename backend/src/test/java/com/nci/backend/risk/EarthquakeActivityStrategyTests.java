package com.nci.backend.risk;

import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.*;
import com.nci.backend.provider.model.NormalizedHazardEvent;
import com.nci.backend.risk.model.RiskLevel;
import com.nci.backend.risk.service.EarthquakeActivityStrategy;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.nci.backend.risk.RiskFixtures.*;
import static org.assertj.core.api.Assertions.*;

class EarthquakeActivityStrategyTests {
    private final EarthquakeActivityStrategy strategy = new EarthquakeActivityStrategy();

    @Test
    void magnitudeBandBoundariesAreExplicit() {
        double[] magnitudes = {-1, 3.999, 4, 4.999, 5, 5.999, 6, 6.999, 7, 8};
        int[] expected = {20, 20, 40, 40, 60, 60, 80, 80, 100, 100};
        for (int i = 0; i < magnitudes.length; i++) {
            assertThat(strategy.assess(snapshot(false, event("test", magnitudes[i], 0, Duration.ZERO))).score())
                    .as("magnitude %s", magnitudes[i]).isEqualTo(expected[i]);
        }
    }

    @Test
    void distanceBandBoundariesAreInclusive() {
        double[] distances = {0, 25, 25.001, 100, 100.001, 250, 250.001, 1000};
        int[] expected = {100, 100, 75, 75, 50, 50, 25, 25};
        for (int i = 0; i < distances.length; i++) {
            assertThat(strategy.assess(snapshot(false, event("test", 7.0, distances[i], Duration.ZERO))).score())
                    .as("distance %s", distances[i]).isEqualTo(expected[i]);
        }
    }

    @Test
    void recencyBandBoundariesAreInclusive() {
        long[] seconds = {0, 86400, 86401, 259200, 259201, 604800, 604801, 2592000};
        int[] expected = {100, 100, 75, 75, 50, 50, 25, 25};
        for (int i = 0; i < seconds.length; i++) {
            assertThat(strategy.assess(snapshot(false, event("test", 7.0, 0, Duration.ofSeconds(seconds[i])))).score())
                    .as("age %s seconds", seconds[i]).isEqualTo(expected[i]);
        }
    }

    @Test
    void factorsExposeReproducibleInputsAndSource() {
        var result = strategy.assess(snapshot(false, event("evidence", 5.2, 60, Duration.ofHours(48))));
        assertThat(result.score()).isEqualTo(34); // round(60 * .75 * .75)
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MODERATE);
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.modelVersion()).isEqualTo("earthquake-activity-v1-experimental");
        var factor = result.factors().getFirst();
        assertThat(factor.sourceUrl()).isEqualTo("https://example.test/evidence");
        assertThat(factor.startedAt()).isEqualTo(NOW.minus(Duration.ofHours(48)));
        assertThat(factor.sourceUpdatedAt()).isEqualTo(factor.startedAt().plusSeconds(1));
        assertThat(factor.magnitude()).isEqualTo(5.2);
        assertThat(factor.magnitudePoints()).isEqualTo(60);
        assertThat(factor.distanceKm()).isEqualTo(60);
        assertThat(factor.ageHours()).isEqualTo(48);
        assertThat(factor.distanceWeight()).isEqualTo(.75);
        assertThat(factor.recencyWeight()).isEqualTo(.75);
        assertThat(factor.contribution()).isEqualTo(34);
    }

    @Test
    void levelsUseDocumentedCutoffs() {
        assertThat(strategy.assess(snapshot(false, event("low", 3.0, 0, Duration.ZERO))).riskLevel()).isEqualTo(RiskLevel.LOW);
        var at25 = strategy.assess(snapshot(false, event("moderate", 7.0, 500, Duration.ZERO)));
        assertThat(at25.score()).isEqualTo(25);
        assertThat(at25.riskLevel()).isEqualTo(RiskLevel.MODERATE);
        var at50 = strategy.assess(snapshot(false, event("high", 7.0, 200, Duration.ZERO)));
        assertThat(at50.score()).isEqualTo(50);
        assertThat(at50.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void strongestContributionWinsWithoutSummingOrDependingOnOrder() {
        var weak = event("a", 3.0, 0, Duration.ZERO);
        var strong = event("b", 6.0, 0, Duration.ZERO);
        var first = strategy.assess(snapshot(false, weak, strong));
        assertThat(first.score()).isEqualTo(80);
        assertThat(strategy.assess(snapshot(false, strong, weak))).isEqualTo(first);
        assertThat(strategy.assess(snapshot(false, strong, strong)).score()).isEqualTo(80);
    }

    @Test
    void emptyOrTruncatedEvidenceNeverImpliesLowRisk() {
        var empty = strategy.assess(snapshot(false));
        assertThat(empty.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(empty.score()).isNull();
        var truncated = strategy.assess(snapshot(true, event("strong", 7.0, 0, Duration.ZERO)));
        assertThat(truncated.score()).isNull();
        assertThat(truncated.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(truncated.limitations()).contains("TRUNCATED_SOURCE_RESULTS");
        assertThat(truncated.factors().getFirst().contribution()).isEqualTo(100);
    }

    @Test
    void missingOrNonFiniteMagnitudeWithholdsScoreButRetainsEvidence() {
        for (Double magnitude : new Double[]{null, Double.NaN, Double.POSITIVE_INFINITY}) {
            var result = strategy.assess(snapshot(false, event("unknown", magnitude, 0, Duration.ZERO),
                    event("known", 7.0, 0, Duration.ZERO)));
            assertThat(result.score()).isNull();
            assertThat(result.confidence()).isEqualTo(ConfidenceLevel.UNKNOWN);
            assertThat(result.limitations()).contains("MISSING_OR_INVALID_MAGNITUDE");
            assertThat(result.factors()).hasSize(2);
            assertThat(result.factors().get(1).magnitude()).isNull();
        }
    }

    @Test
    void futureOutOfWindowAndOutOfRadiusEventsDoNotContribute() {
        var result = strategy.assess(snapshot(false,
                event("future", 7.0, 0, Duration.ofSeconds(-1)),
                event("old", 7.0, 0, Duration.ofDays(31)),
                event("far", 7.0, 1001, Duration.ZERO)));
        assertThat(result.score()).isNull();
        assertThat(result.factors()).isEmpty();
    }

    @Test
    void invalidDistanceWithholdsAssessment() {
        for (double km : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            var result = strategy.assess(snapshot(false, event("invalid", 7.0, km, Duration.ZERO)));
            assertThat(result.score()).isNull();
            assertThat(result.limitations()).contains("INVALID_EVIDENCE");
        }
    }

    @Test
    void otherHazardsAndPredictionsAreNotScoredAsObservedEarthquakes() {
        for (var type : new InformationType[]{InformationType.MODELED_RISK, InformationType.OFFICIAL_WARNING,
                InformationType.EXPERIMENTAL_PREDICTION}) {
            var e = new NormalizedHazardEvent("test", HazardType.EARTHQUAKE, "test", SeverityLevel.HIGH,
                    type, 0, 0, null, NOW, null, "TEST", "https://example.test", NOW, NOW,
                    ConfidenceLevel.UNKNOWN, Map.of("magnitude", 7.0));
            assertThat(strategy.assess(snapshot(false, new NearbyHazardResponse.Item(e, 0))).score()).isNull();
        }
        var flood = new NormalizedHazardEvent("flood", HazardType.FLOOD, "test", SeverityLevel.HIGH,
                InformationType.OBSERVED_EVENT, 0, 0, null, NOW, null, "TEST", "https://example.test",
                NOW, NOW, ConfidenceLevel.UNKNOWN, Map.of("magnitude", 7.0));
        assertThat(strategy.assess(snapshot(false, new NearbyHazardResponse.Item(flood, 0))).factors()).isEmpty();
    }
}
