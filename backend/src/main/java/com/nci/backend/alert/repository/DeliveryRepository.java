package com.nci.backend.alert.repository;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import com.nci.backend.alert.dto.AlertResponse;
@Repository
public class DeliveryRepository {
    public record Job(UUID id,int attempt,PushSubscriptionRepository.Subscription subscription,AlertResponse notification) {}
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public DeliveryRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    private java.sql.Timestamp stamp(Instant now) { return java.sql.Timestamp.from(now); }
    public void recover(Instant now) {
        jdbc.update("update notification_attempt a set completed_at=?,result='DELIVERY_OUTCOME_UNKNOWN' from notification_delivery d "
                + "where a.delivery_id=d.id and a.attempt=d.attempts and d.status='SENDING' and d.lease_until<?",stamp(now),stamp(now));
        jdbc.update("update notification_delivery set status='UNCERTAIN',last_error='DELIVERY_OUTCOME_UNKNOWN',lease_until=null where status='SENDING' and lease_until<?",stamp(now));
    }
    public Optional<Job> claim(Instant now) {
        var ids=jdbc.query("with candidate as (select id from notification_delivery where status='PENDING' and next_attempt_at<=? order by next_attempt_at,id for update skip locked limit 1) "
                + "update notification_delivery d set status='SENDING',attempts=attempts+1,lease_until=? from candidate c where d.id=c.id returning d.id",
                (rs,row)->rs.getObject(1,UUID.class),stamp(now),stamp(now.plusSeconds(60)));
        if(ids.isEmpty()) return Optional.empty();
        UUID id=ids.getFirst();
        var jobs=jdbc.query("select d.attempts,s.id,s.owner_id,s.endpoint,s.p256dh,s.auth_secret,n.payload from notification_delivery d "
                + "join push_subscription s on s.id=d.subscription_id join notification_item n on n.id=d.notification_id where d.id=? and s.owner_id=n.owner_id",
                (rs,row)->new Job(id,rs.getInt(1),new PushSubscriptionRepository.Subscription(rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5),rs.getString(6)),json.readValue(rs.getString(7),AlertResponse.class)),id);
        if(jobs.isEmpty()) { jdbc.update("update notification_delivery set status='CANCELLED',last_error='SUBSCRIPTION_REMOVED',lease_until=null where id=?",id); return Optional.empty(); }
        var job=jobs.getFirst();
        jdbc.update("insert into notification_attempt(delivery_id,attempt,started_at) values (?,?,?)",id,job.attempt(),stamp(now));
        return Optional.of(job);
    }
    public void finish(Job job,String status,String error,Instant now) {
        jdbc.update("update notification_delivery set status=?,last_error=?,next_attempt_at=?,lease_until=null where id=? and status='SENDING' and attempts=?",
                status,error,stamp(now.plusSeconds(job.attempt()==1?60:300)),job.id(),job.attempt());
        jdbc.update("update notification_attempt set completed_at=?,result=? where delivery_id=? and attempt=? and completed_at is null",
                stamp(now),error==null?status:error,job.id(),job.attempt());
    }
}
