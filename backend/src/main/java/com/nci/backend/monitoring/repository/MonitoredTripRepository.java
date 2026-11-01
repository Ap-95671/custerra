package com.nci.backend.monitoring.repository;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import com.nci.backend.monitoring.dto.*;
import com.nci.backend.route.dto.*;
@Repository
public class MonitoredTripRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final com.nci.backend.auth.CurrentAccount current;
    public MonitoredTripRepository(JdbcTemplate jdbc, ObjectMapper json, com.nci.backend.auth.CurrentAccount current) { this.jdbc = jdbc; this.json = json; this.current=current; }
    private java.sql.Timestamp stamp(Instant time) { return time == null ? null : java.sql.Timestamp.from(time); }
    private final RowMapper<MonitoringResponse> mapper = (rs, row) -> new MonitoringResponse(
            rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"),
            route(rs), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("last_attempt_at") == null ? null : rs.getTimestamp("last_attempt_at").toInstant(),
            rs.getTimestamp("last_checked_at") == null ? null : rs.getTimestamp("last_checked_at").toInstant(),
            rs.getString("last_error"), readAssessment(rs.getString("assessment")), checkpoints(rs.getObject("id", UUID.class)),generated(rs),progress(rs));
    private RouteRiskRequest route(java.sql.ResultSet rs) throws java.sql.SQLException {
        var route=readRoute(rs.getString("route"));var encoded=rs.getString("encoded_geometry");
        return encoded==null?route:new RouteRiskRequest(com.nci.backend.route.provider.EncodedRoute.decode(encoded),route.corridorKm(),route.days(),route.minMagnitude());
    }
    private MonitoringResponse.GeneratedGeometry generated(java.sql.ResultSet rs) throws java.sql.SQLException {
        var encoded=rs.getString("encoded_geometry");
        return encoded==null?null:new MonitoringResponse.GeneratedGeometry(encoded,rs.getInt("geometry_version"),rs.getString("origin_name"),rs.getString("destination_name"));
    }
    private JourneyProgress progress(java.sql.ResultSet rs) throws java.sql.SQLException {
        var value=rs.getString("progress");
        if(value==null)return null;
        return json.readValue(value,JourneyProgress.class);
    }
    public MonitoringResponse createGenerated(GeneratedJourneyRequest request,Instant now) {
        UUID id=UUID.randomUUID();
        var parameters=new RouteRiskRequest(java.util.List.of(),request.corridorKm(),request.days(),request.minMagnitude());
        jdbc.update("insert into monitored_journey(id,name,status,route,created_at,owner_id,encoded_geometry,geometry_version,origin_name,destination_name) values (?,?,'PLANNED',?,?,?,?,1,?,?)",
            id,request.name().strip(),json.writeValueAsString(parameters),stamp(now),current.id(),request.geometry(),request.originName().strip(),request.destinationName().strip());
        return find(id,false).orElseThrow();
    }
    public void progress(UUID id,JourneyProgress progress) {
        jdbc.update("update monitored_journey set progress=?,progress_at=? where id=?",progress==null?null:json.writeValueAsString(progress),progress==null?null:stamp(progress.observedAt()),id);
    }
    public void expireProgress(Instant before) {
        jdbc.update("update monitored_journey set progress=null,progress_at=null where progress_at<?",stamp(before));
    }
    private RouteRiskRequest readRoute(String value) { return json.readValue(value, RouteRiskRequest.class); }
    private RouteRiskResponse readAssessment(String value) { return value == null ? null : json.readValue(value, RouteRiskResponse.class); }
    public MonitoringResponse create(MonitoringRequest request, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into monitored_journey(id,name,status,route,created_at,owner_id) values (?,?,'PAUSED',?,?,?)",
                id, request.name().strip(), json.writeValueAsString(request.route()), stamp(now), current.id());
        return find(id, false).orElseThrow();
    }
    public Optional<MonitoringResponse> find(UUID id, boolean lock) {
        return jdbc.query("select * from monitored_journey where id=? and owner_id=?" + (lock ? " for update" : ""), mapper, id, current.id()).stream().findFirst();
    }
    public Optional<MonitoringResponse> findForMonitoring(UUID id, boolean lock) {
        return jdbc.query("select * from monitored_journey where id=? and owner_id is not null" + (lock ? " for update" : ""),mapper,id).stream().findFirst();
    }
    public List<MonitoringResponse> list(int offset) {
        return jdbc.query("select * from monitored_journey where owner_id=? order by created_at desc,id limit 50 offset ?", mapper, current.id(), offset);
    }
    public void status(UUID id, String status) {
        jdbc.update("update monitored_journey set status=? where id=?", status, id);
        if(!status.equals("ACTIVE"))jdbc.update("update notification_delivery d set status='CANCELLED',last_error='JOURNEY_NOT_ACTIVE' from notification_item n where d.notification_id=n.id and n.journey_id=? and d.status='PENDING'",id);
    }
    public void delete(UUID id) { jdbc.update("delete from monitored_journey where id=?", id); }
    public void checked(UUID id, Instant attempt, Instant success, String error, RouteRiskResponse assessment) {
        jdbc.update("update monitored_journey set last_attempt_at=?,last_checked_at=?,last_error=?,assessment=? where id=?",
                stamp(attempt), stamp(success), error, assessment == null ? null : json.writeValueAsString(assessment), id);
    }
    public List<MonitoringResponse.ProviderCheckpoint> checkpoints(UUID id) {
        return jdbc.query("select source,last_success_at from journey_provider_checkpoint where journey_id=? order by source",
                (rs,row) -> new MonitoringResponse.ProviderCheckpoint(rs.getString(1),rs.getTimestamp(2).toInstant()),id);
    }
    public void checkpoint(UUID id, String source, Instant now) {
        jdbc.update("insert into journey_provider_checkpoint(journey_id,source,last_success_at) values (?,?,?) "
                + "on conflict(journey_id,source) do update set last_success_at=excluded.last_success_at",id,source,stamp(now));
    }
    public record EventState(String externalId, String fingerprint, RouteRiskResponse.Match evidence, long version, boolean present) {}
    public Map<String,EventState> states(UUID id, String source) {
        var states = new LinkedHashMap<String,EventState>();
        jdbc.query("select external_id,fingerprint,evidence,state_version,present from journey_seen where journey_id=? and source=?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    var payload = rs.getString(3);
                    var state = new EventState(rs.getString(1),rs.getString(2),payload == null ? null : json.readValue(payload,RouteRiskResponse.Match.class),rs.getLong(4),rs.getBoolean(5));
                    states.put(state.externalId(),state);
                },id,source);
        return states;
    }
    public void remember(UUID journey, RouteRiskResponse.Match match, String fingerprint, long version, boolean present) {
        jdbc.update("insert into journey_evidence_revision(journey_id,source,external_id,state_version,present,evidence) values (?,?,?,?,?,?) on conflict do nothing",
            journey,match.event().source(),match.event().externalId(),version,present,json.writeValueAsString(match));
        jdbc.update("insert into journey_seen(journey_id,source,external_id,fingerprint,evidence,state_version,present) values (?,?,?,?,?,?,?) "
                + "on conflict(journey_id,source,external_id) do update set fingerprint=excluded.fingerprint,evidence=excluded.evidence,state_version=excluded.state_version,present=excluded.present",
                journey,match.event().source(),match.event().externalId(),fingerprint,json.writeValueAsString(match),version,present);
    }
    public record Revision(String source,String externalId,long stateVersion,Instant recordedAt,boolean present,RouteRiskResponse.Match evidence) {}
    public List<Revision> revisions(UUID id,int offset) {
        return jdbc.query("select * from journey_evidence_revision where journey_id=? order by recorded_at desc,source,external_id,state_version desc limit 50 offset ?",
            (rs,row)->new Revision(rs.getString("source"),rs.getString("external_id"),rs.getLong("state_version"),rs.getTimestamp("recorded_at").toInstant(),rs.getBoolean("present"),json.readValue(rs.getString("evidence"),RouteRiskResponse.Match.class)),id,offset);
    }
    public boolean notice(JourneyNotice notice) {
        return jdbc.update("insert into journey_notice(id,journey_id,created_at,source,external_id,payload,notice_type,state_version) values (?,?,?,?,?,?,?,?) on conflict do nothing",
                notice.id(), notice.journeyId(), stamp(notice.createdAt()), notice.evidence().event().source(),
                notice.evidence().event().externalId(), json.writeValueAsString(notice),notice.type(),notice.stateVersion())==1;
    }
    public List<JourneyNotice> notices(UUID id, int offset) {
        return jdbc.query("select payload from journey_notice where journey_id=? order by created_at desc,id limit 50 offset ?",
                (rs,row) -> json.readValue(rs.getString(1), JourneyNotice.class), id, offset);
    }
    public List<UUID> due(Instant before) {
        return jdbc.query("select id from monitored_journey where status='ACTIVE' and owner_id is not null and (last_attempt_at is null or last_attempt_at <= ?) order by last_attempt_at nulls first,created_at,id limit 10",
                (rs,row) -> rs.getObject(1, UUID.class), stamp(before));
    }
}
