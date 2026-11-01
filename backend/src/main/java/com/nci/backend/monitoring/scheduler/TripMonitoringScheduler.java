package com.nci.backend.monitoring.scheduler;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import com.nci.backend.monitoring.repository.MonitoredTripRepository;
import com.nci.backend.monitoring.service.TripMonitoringService;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="custerra.monitoring.enabled", havingValue="true")
public class TripMonitoringScheduler {
    private final MonitoredTripRepository repository;
    private final TripMonitoringService service;
    private final Clock clock;
    private final long interval;
    private final com.nci.backend.operations.WorkerExecutionStatus executions;
    public TripMonitoringScheduler(MonitoredTripRepository repository, TripMonitoringService service, Clock clock,
            @Value("${custerra.monitoring.interval-ms:300000}") long interval, com.nci.backend.operations.WorkerExecutionStatus executions) {
        if (interval < 60000 || interval > 86400000) throw new IllegalArgumentException("Monitoring interval must be 60 seconds to 24 hours");
        this.repository = repository; this.service = service; this.clock = clock; this.interval = interval;
        this.executions=executions;
    }
    @Scheduled(fixedDelayString="${custerra.monitoring.interval-ms:300000}", initialDelayString="${custerra.monitoring.interval-ms:300000}")
    public void refresh() {
        var started=executions.start("journeys");String outcome="COMPLETED";
        try {
        var due = clock.instant().minusMillis(interval);
        for (var id : repository.due(due)) {
            try { var checked=service.check(id, due); if(checked!=null && checked.lastError()!=null)outcome="DEGRADED"; }
            catch (RuntimeException error) {
                outcome="DEGRADED";
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Journey check failed: type={}", error.getClass().getSimpleName());
            }
        }
        } catch(RuntimeException failure){outcome="FAILED";throw failure;}
        finally {executions.finish("journeys",started,outcome);}
    }
}
