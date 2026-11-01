package com.nci.backend.journey;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.nci.backend.monitoring.scheduler.TripMonitoringScheduler;
import com.nci.backend.monitoring.repository.MonitoredTripRepository;
import com.nci.backend.monitoring.service.TripMonitoringService;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class MonitoringSchedulerTests {
    @Test void processesDueBatchAndContinuesAfterOneJourneyFails() {
        var repository = mock(MonitoredTripRepository.class);
        var service = mock(TripMonitoringService.class);
        var now = FeatureFixtures.NOW;
        var due = now.minusSeconds(300);
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        when(repository.due(due)).thenReturn(List.of(first,second));
        when(service.check(first,due)).thenThrow(new IllegalStateException("test failure"));
        new TripMonitoringScheduler(repository,service,Clock.fixed(now,ZoneOffset.UTC),300000,mock(com.nci.backend.operations.WorkerExecutionStatus.class)).refresh();
        verify(service).check(first,due); verify(service).check(second,due);
    }
    @Test void rejectsUnboundedSchedulingFrequency() {
        for (long interval : new long[]{0,59999,86400001})
            assertThatThrownBy(() -> new TripMonitoringScheduler(mock(MonitoredTripRepository.class),
                    mock(TripMonitoringService.class),Clock.systemUTC(),interval,mock(com.nci.backend.operations.WorkerExecutionStatus.class))).isInstanceOf(IllegalArgumentException.class);
    }
}
