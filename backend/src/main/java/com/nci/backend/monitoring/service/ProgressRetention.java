package com.nci.backend.monitoring.service;
import java.time.Clock;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import com.nci.backend.monitoring.repository.MonitoredTripRepository;
/** Local privacy maintenance only: never polls providers or enables journey/notification work. */
@Configuration @EnableScheduling
public class ProgressRetention {
    private final MonitoredTripRepository repository;private final Clock clock;
    public ProgressRetention(MonitoredTripRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    @Scheduled(fixedDelay=3600000,initialDelay=60000)
    public void expire(){repository.expireProgress(clock.instant().minusSeconds(86400));}
}
