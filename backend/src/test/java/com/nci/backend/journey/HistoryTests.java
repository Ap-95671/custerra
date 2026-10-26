package com.nci.backend.journey;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.nci.backend.history.*;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import com.nci.backend.hazard.service.GeoDistance;
import static com.nci.backend.journey.FeatureFixtures.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class HistoryTests {
    private final HistoricalProvider provider = mock(HistoricalProvider.class);
    private final HistoryRepository repository = mock(HistoryRepository.class);
    private final HistoryService service = new HistoryService(provider,repository,new GeoDistance(),Clock.fixed(NOW,ZoneOffset.UTC));
    private final HistoryQuery query = new HistoryQuery(0,0,100,NOW.minus(Duration.ofDays(365)),NOW,4);
    @Test void frequencyUsesExplicitWindowAndThresholdWithExclusiveEnd() {
        when(provider.fetchHistory(query)).thenReturn(new ProviderResult(List.of(event("at-start",0,0,4,query.from()),
                event("at-end",0,0,5,NOW),event("small",0,0,3.9,query.from()),event("far",2,0,5,query.from())),false));
        var result = service.create(query);
        assertThat(result.eventCount()).isEqualTo(1);
        assertThat(result.annualFrequency()).isCloseTo(365.2425/365,within(.000001));
        assertThat(result.events().getFirst().externalId()).isEqualTo("at-start");
        verify(repository).save(result);
    }
    @Test void truncationWithholdsFrequencyAndFailureDoesNotPersist() {
        when(provider.fetchHistory(query)).thenReturn(new ProviderResult(List.of(),true)).thenThrow(new ProviderException(false));
        assertThat(service.create(query).annualFrequency()).isNull();
        assertThatThrownBy(() -> service.create(query)).isInstanceOf(ProviderException.class);
        verify(repository,times(1)).save(any());
    }
    @Test void invalidWindowsAndCoordinatesDoNotFetch() {
        for (var invalid : List.of(new HistoryQuery(0,0,100,NOW,NOW,4),new HistoryQuery(0,0,100,NOW.minusSeconds(86400),NOW.plusSeconds(1),4),
                new HistoryQuery(0,0,100,NOW.minus(Duration.ofDays(3661)),NOW,4),new HistoryQuery(Double.NaN,0,100,query.from(),NOW,4)))
            assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(provider,repository);
    }
}
