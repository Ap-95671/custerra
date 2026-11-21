package com.nci.backend.intelligence;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
@Repository
public class SavedLocationRepository {
    public record Location(UUID id,String name,double latitude,double longitude,double radiusKm,int days,boolean monitoringOptIn,Instant createdAt,Instant lastAttemptAt) {}
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public SavedLocationRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    private final org.springframework.jdbc.core.RowMapper<Location> mapper=(r,n)->new Location(r.getObject("id",UUID.class),r.getString("name"),r.getDouble("latitude"),r.getDouble("longitude"),r.getDouble("radius_km"),r.getInt("lookback_days"),r.getBoolean("monitoring_opt_in"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("last_attempt_at")==null?null:r.getTimestamp("last_attempt_at").toInstant());
    public List<Location> list(UUID owner){return jdbc.query("select * from saved_location where owner_id=? order by created_at desc,id limit 20",mapper,owner);}
    public Location get(UUID owner,UUID id){return jdbc.query("select * from saved_location where id=? and owner_id=?",mapper,id,owner).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    @Transactional public Location create(UUID owner,SavedLocationController.Create request,Instant now){
        jdbc.queryForObject("select id from user_account where id=? for update",UUID.class,owner);
        if(jdbc.queryForObject("select count(*) from saved_location where owner_id=?",Integer.class,owner)>=20)throw new ResponseStatusException(HttpStatus.CONFLICT);
        UUID id=UUID.randomUUID();jdbc.update("insert into saved_location(id,owner_id,name,latitude,longitude,radius_km,lookback_days,created_at) values (?,?,?,?,?,?,?,?)",id,owner,request.name().strip(),request.latitude(),request.longitude(),request.radiusKm(),request.days(),java.sql.Timestamp.from(now));return get(owner,id);
    }
    public Location monitoring(UUID owner,UUID id,boolean enabled){if(jdbc.update("update saved_location set monitoring_opt_in=? where id=? and owner_id=?",enabled,id,owner)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);return get(owner,id);}
    public void delete(UUID owner,UUID id){if(jdbc.update("delete from saved_location where id=? and owner_id=?",id,owner)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    public List<SavedLocationService.Check> history(UUID owner,UUID id){get(owner,id);return jdbc.query("select payload::text from saved_location_check where location_id=? order by checked_at desc,id limit 30",(r,n)->json.readValue(r.getString(1),SavedLocationService.Check.class),id);}
    @Transactional public SavedLocationService.Check record(UUID owner,UUID id,IntelligenceService.Response evidence,Instant now){
        var raw=jdbc.query("select baseline::text from saved_location where id=? and owner_id=? for update",(r,n)->r.getString(1),id,owner);if(raw.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var baseline=json.readTree(raw.getFirst());var next=json.createObjectNode();next.setAll((tools.jackson.databind.node.ObjectNode)baseline);
        var changes=new ArrayList<SavedLocationService.Change>();
        for(var source:evidence.sources()){
            if("UNAVAILABLE".equals(source.coverageStatus()))continue;
            var previous=baseline.path(source.source());var state=json.createObjectNode();
            for(var item:evidence.items())if(item.event().source().equals(source.source())){
                var event=item.event();String identity=event.id();String fingerprint=json.writeValueAsString(List.of(event.title(),event.severity(),event.messageType(),item.state(),event.updatedAt().toString(),event.geometry()==null?"":json.writeValueAsString(event.geometry()),event.description(),event.urgency(),event.certainty(),String.valueOf(event.effectiveAt()),String.valueOf(event.expiresAt()),event.attributes(),event.references(),event.sourceLinks()));
                if(previous.isObject()&&!previous.has(identity))changes.add(new SavedLocationService.Change(source.source(),identity,"NEW_EVIDENCE"));
                else if(previous.has(identity)&&!previous.path(identity).asText().equals(fingerprint))changes.add(new SavedLocationService.Change(source.source(),identity,"UPDATED_EVIDENCE"));
                state.put(identity,fingerprint);
            }
            // Partial feeds cannot prove disappearance; retain previously known identities in the baseline.
            if(previous.isObject())previous.properties().forEach(e->{if(!state.has(e.getKey()))state.set(e.getKey(),e.getValue());});
            while(state.size()>1000)state.remove(state.properties().iterator().next().getKey());
            next.set(source.source(),state);
        }
        var check=new SavedLocationService.Check(UUID.randomUUID(),id,now,evidence,changes);
        jdbc.update("insert into saved_location_check(id,location_id,checked_at,payload) values (?,?,?,?::jsonb)",check.id(),id,java.sql.Timestamp.from(now),json.writeValueAsString(check));
        jdbc.update("update saved_location set last_attempt_at=?,baseline=?::jsonb where id=? and owner_id=?",java.sql.Timestamp.from(now),json.writeValueAsString(next),id,owner);
        jdbc.update("delete from saved_location_check where id in (select id from saved_location_check where location_id=? order by checked_at desc,id offset 30)",id);return check;
    }
    public record Due(UUID id,UUID owner){}
    public List<Due> due(Instant before){return jdbc.query("select id,owner_id from saved_location where monitoring_opt_in=true and (last_attempt_at is null or last_attempt_at<?) order by last_attempt_at nulls first,created_at,id limit 10",(r,n)->new Due(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),java.sql.Timestamp.from(before));}
}
