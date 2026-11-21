package com.nci.backend.intelligence;

import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.history.HistoryQuery;
import com.nci.backend.history.HistoryService;
import com.nci.backend.history.HistorySnapshot;
import com.nci.backend.provider.ProviderException;
import com.nci.backend.provider.model.ProviderMetadata;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HistoricalComparisonTests {
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final Instant FROM = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-01T00:00:00Z");
    private final HistoryService history = mock(HistoryService.class);
    private final IntelligenceService intelligence = mock(IntelligenceService.class);
    private final ComparisonController controller = new ComparisonController(mock(NearbyHazardService.class),
            intelligence, history, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final List<ComparisonController.Destination> destinations = List.of(
            new ComparisonController.Destination("First", 12d, 40d),
            new ComparisonController.Destination("Second", 13d, 42d),
            new ComparisonController.Destination("Third", 14d, 44d));

    @Test void equalHistoricalFiltersAndIndependentCoverage() {
        when(history.preview(any())).thenAnswer(invocation -> {
            HistoryQuery query = invocation.getArgument(0);
            assertThat(query.from()).isEqualTo(FROM);
            assertThat(query.to()).isEqualTo(TO);
            assertThat(query.radiusKm()).isEqualTo(100);
            assertThat(query.minMagnitude()).isEqualTo(4);
            if (query.latitude() == 13) throw new ProviderException(false);
            boolean truncated = query.latitude() == 14;
            return new HistorySnapshot(UUID.randomUUID(), "catalog-frequency-v1", query, NOW,
                    truncated ? 2000 : 4, 2, truncated ? null : 2d, truncated, List.of(),
                    new ProviderMetadata("USGS", "USGS", "https://earthquake.usgs.gov", "https://usgs.gov", "Catalog"), "");
        });
        var result = controller.compare(new ComparisonController.Compare(destinations, 100d, 7,
                new ComparisonController.HistoricalWindow(FROM, TO, 4d)));
        assertThat(result.historicalWindow().from()).isEqualTo(FROM);
        assertThat(result.destinations()).extracting(destination -> destination.history().coverageStatus())
                .containsExactly("RETRIEVED", "UNAVAILABLE", "TRUNCATED");
        assertThat(result.destinations().get(0).history().annualFrequency()).isEqualTo(2);
        assertThat(result.destinations().get(1).history().eventCount()).isNull();
        assertThat(result.destinations().get(2).history().annualFrequency()).isNull();
    }

    @Test void windowValidationPrecedesAnySourceCallsAndDefaultMakesNoHistoricalCalls() {
        assertThatThrownBy(() -> controller.compare(new ComparisonController.Compare(destinations, 100d, 7,
                new ComparisonController.HistoricalWindow(TO, FROM, 4d)))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(intelligence, history);

        var result = controller.compare(new ComparisonController.Compare(destinations, 100d, 7, null));
        assertThat(result.historicalWindow()).isNull();
        assertThat(result.destinations()).allSatisfy(destination -> assertThat(destination.history()).isNull());
        verifyNoInteractions(history);
    }
}
