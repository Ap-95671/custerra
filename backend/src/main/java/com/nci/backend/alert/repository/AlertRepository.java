package com.nci.backend.alert.repository;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import com.nci.backend.alert.dto.*;
import com.nci.backend.monitoring.dto.JourneyNotice;
@Repository
public class AlertRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AlertRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    public NotificationPreference preferences(UUID owner) {
        return jdbc.query("select * from notification_preference where owner_id=?",(rs,row)->new NotificationPreference(rs.getBoolean("enabled"),rs.getBoolean("push_enabled"),
                Set.of(json.readValue(rs.getString("hazard_types"),com.nci.backend.hazard.model.HazardType[].class)),
                com.nci.backend.hazard.model.SeverityLevel.valueOf(rs.getString("minimum_severity")),rs.getBoolean("include_unknown")),owner).stream().findFirst().orElse(NotificationPreference.defaults());
    }
    public void preferences(UUID owner,NotificationPreference p) {
        jdbc.update("insert into notification_preference(owner_id,enabled,push_enabled,hazard_types,minimum_severity,include_unknown) values (?,?,?,?,?,?) "
                + "on conflict(owner_id) do update set enabled=excluded.enabled,push_enabled=excluded.push_enabled,hazard_types=excluded.hazard_types,minimum_severity=excluded.minimum_severity,include_unknown=excluded.include_unknown",
                owner,p.enabled(),p.pushEnabled(),json.writeValueAsString(p.hazardTypes()),p.minimumSeverity().name(),p.includeUnknown());
    }
    public record JourneyOwner(UUID owner,String name) {}
    public boolean active(UUID journey) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from monitored_journey where id=? and status='ACTIVE' and owner_id is not null)",Boolean.class,journey));
    }
    public Optional<JourneyOwner> owner(UUID journey) {
        return jdbc.query("select owner_id,name from monitored_journey where id=? and owner_id is not null",(rs,row)->new JourneyOwner(rs.getObject(1,UUID.class),rs.getString(2)),journey).stream().findFirst();
    }
    public void record(UUID owner,String journeyName,JourneyNotice notice,boolean push) {
        int inserted=jdbc.update("insert into notification_item(id,owner_id,journey_id,created_at,payload) values (?,?,?,?,?) on conflict do nothing",
                notice.id(),owner,notice.journeyId(),java.sql.Timestamp.from(notice.createdAt()),json.writeValueAsString(new AlertResponse(notice.id(),journeyName,notice,null,List.of())));
        if(inserted==1 && push) jdbc.update("insert into notification_delivery(id,notification_id,subscription_id,status,next_attempt_at) "
                + "select gen_random_uuid(),?,id,'PENDING',? from push_subscription where owner_id=? on conflict do nothing",notice.id(),java.sql.Timestamp.from(notice.createdAt()),owner);
    }
    private AlertResponse read(java.sql.ResultSet rs) throws java.sql.SQLException {
        var stored=json.readValue(rs.getString("payload"),AlertResponse.class);
        var read=rs.getTimestamp("read_at");
        var deliveries=jdbc.query("select status,attempts,last_error from notification_delivery where notification_id=? order by id",
                (r,row)->new AlertResponse.Delivery("WEB_PUSH",r.getString(1),r.getInt(2),r.getString(3)),stored.id());
        return new AlertResponse(stored.id(),stored.journeyName(),stored.notice(),read==null?null:read.toInstant(),deliveries);
    }
    public List<AlertResponse> list(UUID owner,int offset) {
        return jdbc.query("select * from notification_item where owner_id=? order by created_at desc,id limit 50 offset ?",(rs,row)->read(rs),owner,offset);
    }
    public Optional<AlertResponse> find(UUID owner,UUID id) {
        return jdbc.query("select * from notification_item where id=? and owner_id=?",(rs,row)->read(rs),id,owner).stream().findFirst();
    }
    public boolean markRead(UUID owner,UUID id,Instant now) {
        return jdbc.update("update notification_item set read_at=coalesce(read_at,?) where id=? and owner_id=?",java.sql.Timestamp.from(now),id,owner)==1;
    }
}
