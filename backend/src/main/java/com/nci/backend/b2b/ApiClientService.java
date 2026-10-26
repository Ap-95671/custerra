package com.nci.backend.b2b;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class ApiClientService {
    public record Client(UUID id,String name,String suffix,Instant createdAt,Instant expiresAt,Instant revokedAt,Instant lastUsedAt) {}
    public record Issued(UUID id,String apiKey,Instant expiresAt) { @Override public String toString(){return "Issued[id="+id+",apiKey=redacted]";} }
    public record Admission(UUID requestId,UUID clientId,UUID organizationId) {}
    public static class QuotaExceeded extends ResponseStatusException {
        public final long retrySeconds;
        public QuotaExceeded(long retrySeconds){super(HttpStatus.TOO_MANY_REQUESTS);this.retrySeconds=retrySeconds;}
    }
    public record Usage(UUID organizationId,String plan,int dailyLimit,int minuteLimit,int requestsToday) {}
    private final JdbcTemplate jdbc;private final Clock clock;private final SecureRandom random=new SecureRandom();
    public ApiClientService(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    private java.sql.Timestamp stamp(Instant value){return java.sql.Timestamp.from(value);}
    private Instant instant(java.sql.ResultSet rs,String name)throws java.sql.SQLException{var value=rs.getTimestamp(name);return value==null?null:value.toInstant();}
    private String hash(String key){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private UUID organization(UUID owner){
        jdbc.queryForObject("select id from user_account where id=? for update",UUID.class,owner);
        jdbc.update("insert into api_organization(id,owner_id,created_at) values (?,?,?) on conflict(owner_id) do nothing",UUID.randomUUID(),owner,stamp(clock.instant()));
        return jdbc.queryForObject("select id from api_organization where owner_id=?",UUID.class,owner);
    }
    @Transactional public Issued create(UUID owner,String name){return issue(organization(owner),name);}
    private Issued issue(UUID organization,String name){
        var now=clock.instant();
        if(jdbc.queryForObject("select count(*) from api_client where organization_id=? and revoked_at is null and expires_at>?",Integer.class,organization,stamp(now))>=5)throw new ResponseStatusException(HttpStatus.CONFLICT);
        byte[] bytes=new byte[32];random.nextBytes(bytes);String key="cst_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);UUID id=UUID.randomUUID();Instant expires=now.plus(90,ChronoUnit.DAYS);
        jdbc.update("insert into api_client(id,organization_id,name,key_hash,key_suffix,created_at,expires_at) values (?,?,?,?,?,?,?)",id,organization,name,hash(key),key.substring(key.length()-8),stamp(now),stamp(expires));
        return new Issued(id,key,expires);
    }
    public List<Client> list(UUID owner){return jdbc.query("select c.* from api_client c join api_organization o on c.organization_id=o.id where o.owner_id=? order by c.created_at desc,c.id limit 100",(r,n)->new Client(r.getObject("id",UUID.class),r.getString("name"),r.getString("key_suffix"),instant(r,"created_at"),instant(r,"expires_at"),instant(r,"revoked_at"),instant(r,"last_used_at")),owner);}
    @Transactional public void revoke(UUID owner,UUID id){
        if(jdbc.update("update api_client c set revoked_at=coalesce(revoked_at,?) from api_organization o where c.organization_id=o.id and o.owner_id=? and c.id=?",stamp(clock.instant()),owner,id)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @Transactional public Issued rotate(UUID owner,UUID id){
        UUID org=organization(owner);
        var names=jdbc.query("select name from api_client where id=? and organization_id=? and revoked_at is null for update",(r,n)->r.getString(1),id,org);
        if(names.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        revoke(owner,id);return issue(org,names.getFirst());
    }
    private int limit(String plan,boolean day){return plan.equals("STANDARD")?(day?10000:120):(day?100:20);}
    private int count(UUID org,Instant start,String kind){return jdbc.queryForObject("select coalesce(sum(requests),0) from api_usage_bucket where organization_id=? and window_start=? and window_kind=?",Integer.class,org,stamp(start),kind);}
    private void increment(UUID org,Instant start,String kind){jdbc.update("insert into api_usage_bucket(organization_id,window_start,window_kind,requests) values (?,?,?,1) on conflict(organization_id,window_start,window_kind) do update set requests=api_usage_bucket.requests+1",org,stamp(start),kind);}
    @Transactional public Admission admit(String key,String endpoint){
        if(key==null||!key.matches("cst_[A-Za-z0-9_-]{43}"))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var now=clock.instant();
        // Lock the shared organization before reserving either quota: multiple keys cannot bypass it.
        var rows=jdbc.query("select c.id,c.organization_id,o.plan from api_client c join api_organization o on o.id=c.organization_id where c.key_hash=? and c.revoked_at is null and c.expires_at>? for update of o,c",
            (r,n)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3)},hash(key),stamp(now));
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var row=rows.getFirst();UUID id=(UUID)row[0],org=(UUID)row[1];String plan=(String)row[2];
        var day=now.truncatedTo(ChronoUnit.DAYS);var minute=now.truncatedTo(ChronoUnit.MINUTES);
        if(count(org,day,"DAY")>=limit(plan,true))throw new QuotaExceeded(Math.max(1,Duration.between(now,day.plus(1,ChronoUnit.DAYS)).toSeconds()+1));
        if(count(org,minute,"MINUTE")>=limit(plan,false))throw new QuotaExceeded(Math.max(1,Duration.between(now,minute.plusSeconds(60)).toSeconds()+1));
        increment(org,day,"DAY");increment(org,minute,"MINUTE");
        UUID request=UUID.randomUUID();jdbc.update("insert into api_usage_record(id,client_id,started_at,endpoint) values (?,?,?,?)",request,id,stamp(now),endpoint);
        jdbc.update("update api_client set last_used_at=? where id=?",stamp(now),id);
        return new Admission(request,id,org);
    }
    public void finish(Admission request,int status,long millis){jdbc.update("update api_usage_record set status=?,duration_ms=? where id=?",status,millis,request.requestId());}
    public Usage usage(UUID owner){
        var rows=jdbc.query("select id,plan from api_organization where owner_id=?",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),owner);
        if(rows.isEmpty())return new Usage(null,"DEVELOPMENT",100,20,0);
        var org=rows.getFirst();return new Usage(org.getKey(),org.getValue(),limit(org.getValue(),true),limit(org.getValue(),false),count(org.getKey(),clock.instant().truncatedTo(ChronoUnit.DAYS),"DAY"));
    }
    public void plan(UUID org,String plan){if(jdbc.update("update api_organization set plan=? where id=?",plan,org)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
}
