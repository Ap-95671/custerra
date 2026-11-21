package com.nci.backend.operations;

import java.time.Clock;
import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Aggregate durable work state; no account, location, endpoint or credential data. */
@Component
public class WorkloadStatus {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean monitoringEnabled, notificationsEnabled;
    private final long interval;
    public WorkloadStatus(JdbcTemplate jdbc, Clock clock,
            @Value("${custerra.monitoring.enabled:false}") boolean monitoringEnabled,
            @Value("${custerra.notifications.enabled:false}") boolean notificationsEnabled,
            @Value("${custerra.monitoring.interval-ms:300000}") long interval) {
        this.jdbc=jdbc; this.clock=clock; this.monitoringEnabled=monitoringEnabled;
        this.notificationsEnabled=notificationsEnabled; this.interval=interval;
    }
    public record Snapshot(java.time.Instant checkedAt, boolean monitoringEnabled, boolean notificationsEnabled,
            java.util.Map<String,Object> journeys, java.util.Map<String,Object> notifications) {}
    public Snapshot snapshot() {
        var now=clock.instant();
        var journeys=jdbc.queryForMap("select count(*) filter (where status='ACTIVE') as active, "
            + "count(*) filter (where status='ACTIVE' and (last_attempt_at is null or last_attempt_at<=?)) as due, "
            + "count(*) filter (where status='ACTIVE' and last_error is not null) as degraded, "
            + "min(coalesce(last_checked_at,created_at)) filter (where status='ACTIVE') as oldest_success_or_creation "
            + "from monitored_journey", Timestamp.from(now.minusMillis(interval)));
        var notifications=jdbc.queryForMap("select count(*) filter (where status='PENDING') as pending, "
            + "count(*) filter (where status='PENDING' and next_attempt_at<=?) as due, "
            + "count(*) filter (where status='SENDING' and lease_until<?) as expired_leases, "
            + "count(*) filter (where status='FAILED') as failed, count(*) filter (where status='UNCERTAIN') as uncertain, "
            + "min(next_attempt_at) filter (where status='PENDING') as oldest_pending_at from notification_delivery",
            Timestamp.from(now),Timestamp.from(now));
        return new Snapshot(now,monitoringEnabled,notificationsEnabled,journeys,notifications);
    }
}
