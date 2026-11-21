package com.nci.backend.intelligence;
import java.time.*;
import java.util.*;
import com.nci.backend.monitoring.repository.MonitoredTripRepository;
import com.nci.backend.monitoring.service.JourneyProgressPolicy;
import com.nci.backend.auth.CurrentAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
@RestController @RequestMapping("/api/v1/journeys/{id}/areas")
public class JourneyIntelligenceController {
    public record SavedAreas(UUID journeyId,Instant lastAttemptAt,Integer fromSegment,IntelligenceService.RouteExposure assessment,String error){}
    private final MonitoredTripRepository journeys;private final JourneyProgressPolicy progress;private final IntelligenceService service;private final JdbcTemplate jdbc;private final ObjectMapper json;private final Clock clock;private final CurrentAccount account;
    public JourneyIntelligenceController(MonitoredTripRepository journeys,JourneyProgressPolicy progress,IntelligenceService service,JdbcTemplate jdbc,ObjectMapper json,Clock clock,CurrentAccount account){this.journeys=journeys;this.progress=progress;this.service=service;this.jdbc=jdbc;this.json=json;this.clock=clock;this.account=account;}
    @GetMapping public SavedAreas get(@PathVariable UUID id){journeys.find(id,false).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));return jdbc.query("select payload::text from journey_area_evidence where journey_id=?",(r,n)->json.readValue(r.getString(1),SavedAreas.class),id).stream().findFirst().orElse(new SavedAreas(id,null,null,null,null));}
    @PostMapping public SavedAreas check(@PathVariable UUID id){
        var journey=journeys.find(id,false).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));int start=journey.status().equals("ACTIVE")?progress.startSegment(journey.progress()):0;
        var points=journey.route().points();start=Math.min(start,points.size()-2);var result=service.route(points.subList(start,points.size()));
        var previous=get(id);SavedAreas saved;
        if(result.coverageStatus().equals("UNAVAILABLE")&&previous.assessment()!=null)saved=new SavedAreas(id,clock.instant(),previous.fromSegment(),previous.assessment(),"SOURCES_UNAVAILABLE_PREVIOUS_ASSESSMENT_RETAINED");
        else{final int offset=start;var matches=result.items().stream().map(item->new IntelligenceService.RouteItem(item.event(),item.state(),new SpatialGeometry.RouteMatch(item.spatial().intersects(),item.spatial().lengthWithinKm(),item.spatial().segmentIndices().stream().map(i->i+offset).toList(),item.spatial().method()))).toList();var assessment=new IntelligenceService.RouteExposure(result.assessedAt(),result.overallRisk(),result.coverageStatus(),matches,result.sources(),result.truncated(),result.limitations());saved=new SavedAreas(id,clock.instant(),start,assessment,null);}
        int changed=jdbc.update("insert into journey_area_evidence(journey_id,payload) select id,?::jsonb from monitored_journey where id=? and owner_id=? on conflict(journey_id) do update set payload=excluded.payload",json.writeValueAsString(saved),id,account.id());if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);return saved;
    }
}
