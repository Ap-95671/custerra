package com.nci.backend.hazard;

import com.nci.backend.hazard.entity.HazardEvent;
import com.nci.backend.hazard.model.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HazardEventValidationTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @Test
    void acceptsEverySupportedHazardAndUnknownSourceAssessments() {
        for (HazardType type : HazardType.values()) {
            assertThat(validator.validate(HazardFixtures.event("test", type,
                    SeverityLevel.UNKNOWN, HazardFixtures.START))).isEmpty();
        }
    }

    @Test
    void coordinatesRejectOutOfRangeAndNonFiniteValues() {
        for (double latitude : new double[]{-90.01, 90.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThat(validator.validate(event(latitude, 0, null, null, "https://example.test")))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("latitude"));
        }
        for (double longitude : new double[]{-180.01, 180.01, Double.NaN, Double.NEGATIVE_INFINITY}) {
            assertThat(validator.validate(event(0, longitude, null, null, "https://example.test")))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("longitude"));
        }
    }

    @Test
    void allowsCoordinateBoundariesAndUnknownRadius() {
        assertThat(validator.validate(event(-90, -180, null, null, "https://example.test"))).isEmpty();
        assertThat(validator.validate(event(90, 180, 0.0, HazardFixtures.START, "http://example.test"))).isEmpty();
    }

    @Test
    void radiusRejectsNegativeNonFiniteAndBeyondGlobalDistance() {
        for (double radius : new double[]{-1, 20041, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThat(validator.validate(event(0, 0, radius, null, "https://example.test")))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("affectedRadiusKm"));
        }
    }

    @Test
    void rejectsInvertedTimeRangeAndUnsafeSourceScheme() {
        assertThat(validator.validate(event(0, 0, null, HazardFixtures.START.minusSeconds(1),
                "javascript:alert(1)"))).extracting(v -> v.getPropertyPath().toString())
                .contains("timeRangeValid", "sourceUrl");
    }

    @Test
    void requiresIdentityProvenanceAndClassification() {
        var event = new HazardEvent(" ", null, "", null, null, null, 0, 0, null,
                null, null, "", "", null, null);
        assertThat(validator.validate(event)).extracting(v -> v.getPropertyPath().toString())
                .contains("externalId", "hazardType", "title", "severity", "informationType",
                        "startedAt", "source", "sourceUrl", "observedAt", "confidence");
    }

    private HazardEvent event(double latitude, double longitude, Double radius,
                              Instant endedAt, String sourceUrl) {
        return new HazardEvent("test", HazardType.EARTHQUAKE, "Test-only event", null,
                SeverityLevel.UNKNOWN, InformationType.OBSERVED_EVENT, latitude, longitude,
                radius, HazardFixtures.START, endedAt, "test-source", sourceUrl,
                HazardFixtures.START, ConfidenceLevel.UNKNOWN);
    }
}
