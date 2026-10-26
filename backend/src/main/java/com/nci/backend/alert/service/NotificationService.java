package com.nci.backend.alert.service;
import org.springframework.stereotype.Service;
import com.nci.backend.alert.repository.AlertRepository;
import com.nci.backend.monitoring.dto.JourneyNotice;
/** Eligibility and queue creation participate in the monitoring transaction; no network I/O here. */
@Service
public class NotificationService {
    private final AlertRepository repository;
    public NotificationService(AlertRepository repository) { this.repository=repository; }
    public void noticeCreated(JourneyNotice notice) {
        repository.owner(notice.journeyId()).ifPresent(journey->{
            var preference=repository.preferences(journey.owner());var event=notice.evidence().event();
            if(preference.accepts(event.hazardType(),event.severity())) repository.record(journey.owner(),journey.name(),notice,preference.pushEnabled());
        });
    }
}
