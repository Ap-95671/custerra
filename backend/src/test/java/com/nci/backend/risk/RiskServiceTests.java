package com.nci.backend.risk;

import com.nci.backend.hazard.model.*;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.provider.ProviderException;
import com.nci.backend.risk.model.RiskLevel;
import com.nci.backend.risk.service.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.nci.backend.risk.RiskFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RiskServiceTests {
    @Test
    void fetchesOnceAndKeepsOverallAndUnsupportedHazardsUnknown() {
        var nearby = mock(NearbyHazardService.class);
        when(nearby.earthquakes(0, 0, 1000, 30)).thenReturn(snapshot(false, event("test", 7.0, 0, Duration.ZERO)));
        var service = new RiskService(nearby, new RiskScoringService(List.of(new EarthquakeActivityStrategy())));
        var result = service.assess(0, 0, 1000, 30);
        assertThat(result.overallRisk()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(result.score()).isNull();
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.UNKNOWN);
        assertThat(result.informationType()).isEqualTo(InformationType.MODELED_RISK);
        assertThat(result.hazards()).hasSize(HazardType.values().length);
        assertThat(result.hazards().getFirst().score()).isEqualTo(100);
        assertThat(result.hazards().subList(1, result.hazards().size())).allSatisfy(h -> {
            assertThat(h.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
            assertThat(h.score()).isNull();
            assertThat(h.limitations()).contains("GEOGRAPHICAL_RELEVANCE_NOT_ASSESSED");
        });
        assertThat(result.dataSources().getFirst().id()).isEqualTo("TEST");
        assertThat(result.assessedAt()).isEqualTo(NOW);
        verify(nearby, times(1)).earthquakes(0, 0, 1000, 30);
    }

    @Test
    void providerFailurePropagatesWithoutScoringOrFallback() {
        var nearby = mock(NearbyHazardService.class);
        var scoring = mock(RiskScoringService.class);
        when(nearby.earthquakes(0, 0, 100, 7)).thenThrow(new ProviderException(true));
        assertThatThrownBy(() -> new RiskService(nearby, scoring).assess(0, 0, 100, 7))
                .isInstanceOf(ProviderException.class);
        verifyNoInteractions(scoring);
    }

    @Test
    void missingStrategiesRemainUnknownAndDuplicateStrategiesFailFast() {
        assertThat(new RiskScoringService(List.of()).assess(snapshot(false)))
                .allSatisfy(h -> assertThat(h.score()).isNull());
        assertThatThrownBy(() -> new RiskScoringService(List.of(new EarthquakeActivityStrategy(),
                new EarthquakeActivityStrategy()))).isInstanceOf(IllegalArgumentException.class);
    }
}
