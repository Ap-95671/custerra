package com.nci.backend.alert.service;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="custerra.notifications.enabled",havingValue="true")
public class NotificationScheduler {
    private final NotificationDispatcher dispatcher;
    private final com.nci.backend.operations.WorkerExecutionStatus executions;
    public NotificationScheduler(NotificationDispatcher dispatcher,com.nci.backend.operations.WorkerExecutionStatus executions) { this.dispatcher=dispatcher;this.executions=executions; }
    @Scheduled(fixedDelay=30000,initialDelay=30000) public void deliver() {
        var started=executions.start("notifications");String outcome="COMPLETED";
        try { dispatcher.dispatch(); }
        catch(RuntimeException failure) { outcome="FAILED";org.slf4j.LoggerFactory.getLogger(getClass()).warn("Notification dispatch failed: type={}",failure.getClass().getSimpleName()); }
        finally {executions.finish("notifications",started,outcome);}
    }
}
