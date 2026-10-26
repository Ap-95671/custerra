package com.nci.backend.alert.service;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="custerra.notifications.enabled",havingValue="true")
public class NotificationScheduler {
    private final NotificationDispatcher dispatcher;
    public NotificationScheduler(NotificationDispatcher dispatcher) { this.dispatcher=dispatcher; }
    @Scheduled(fixedDelay=30000,initialDelay=30000) public void deliver() {
        try { dispatcher.dispatch(); }
        catch(RuntimeException failure) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Notification dispatch failed: type={}",failure.getClass().getSimpleName()); }
    }
}
