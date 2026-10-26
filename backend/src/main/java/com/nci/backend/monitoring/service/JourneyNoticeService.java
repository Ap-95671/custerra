package com.nci.backend.monitoring.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nci.backend.monitoring.dto.JourneyNotice;
import com.nci.backend.monitoring.repository.MonitoredTripRepository;
import com.nci.backend.alert.service.NotificationService;
@Service
public class JourneyNoticeService {
    private final MonitoredTripRepository repository;
    private final NotificationService notifications;
    public JourneyNoticeService(MonitoredTripRepository repository,NotificationService notifications) { this.repository=repository;this.notifications=notifications; }
    @Transactional public void create(JourneyNotice notice) { if(repository.notice(notice)) notifications.noticeCreated(notice); }
}
