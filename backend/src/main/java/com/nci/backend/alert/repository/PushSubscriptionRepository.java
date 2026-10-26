package com.nci.backend.alert.repository;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.nci.backend.alert.provider.PushSubscriptionValidator;
@Repository
public class PushSubscriptionRepository {
    public static final String SESSION_ATTRIBUTE="custerraPushSubscription";
    public record Subscription(UUID id,UUID owner,String endpoint,String p256dh,String auth) {
        @Override public String toString() { return "Subscription[id="+id+"]"; }
    }
    private final JdbcTemplate jdbc;
    public PushSubscriptionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional public UUID save(UUID owner,String endpoint,String p256dh,String auth) {
        try { PushSubscriptionValidator.validateEndpoint(endpoint);PushSubscriptionValidator.validateKeys(p256dh,auth); }
        catch(IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        jdbc.queryForObject("select id from user_account where id=? for update",UUID.class,owner);
        String hash=hash(endpoint);
        var existing=jdbc.query("select id,owner_id from push_subscription where endpoint_hash=?",(rs,row)->Map.entry(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)),hash);
        if(!existing.isEmpty()) {
            if(!existing.getFirst().getValue().equals(owner)) throw new ResponseStatusException(HttpStatus.CONFLICT);
            var id=existing.getFirst().getKey();jdbc.update("update push_subscription set p256dh=?,auth_secret=? where id=?",p256dh,auth,id);return id;
        }
        if(jdbc.queryForObject("select count(*) from push_subscription where owner_id=?",Integer.class,owner)>=5) throw new ResponseStatusException(HttpStatus.CONFLICT);
        UUID id=UUID.randomUUID();
        try { jdbc.update("insert into push_subscription(id,owner_id,endpoint_hash,endpoint,p256dh,auth_secret) values (?,?,?,?,?,?)",id,owner,hash,endpoint,p256dh,auth); }
        catch(org.springframework.dao.DuplicateKeyException conflict) { throw new ResponseStatusException(HttpStatus.CONFLICT); }
        return id;
    }
    @Transactional public void remove(UUID owner,String endpoint) {
        var ids=jdbc.query("select id from push_subscription where owner_id=? and endpoint_hash=?",(rs,row)->rs.getObject(1,UUID.class),owner,hash(endpoint));
        ids.forEach(this::expire);
    }
    @Transactional public void remove(UUID owner,UUID id) {
        if(jdbc.queryForObject("select count(*) from push_subscription where owner_id=? and id=?",Integer.class,owner,id)==1) expire(id);
    }
    @Transactional public void expire(UUID id) {
        jdbc.update("update notification_delivery set status='CANCELLED',last_error='SUBSCRIPTION_REMOVED' where subscription_id=? and status='PENDING'",id);
        jdbc.update("delete from push_subscription where id=?",id);
    }
    private String hash(String endpoint) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
