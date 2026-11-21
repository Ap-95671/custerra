package com.nci.backend.monitoring.service;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;
import com.nci.backend.monitoring.dto.*;
import com.nci.backend.monitoring.repository.MonitoredTripRepository;
import com.nci.backend.route.service.*;
import com.nci.backend.provider.ProviderException;
@Service
public class TripMonitoringService {
    private final MonitoredTripRepository repository;
    private final RouteRiskService risk;
    private final RouteGeometry geometry;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final JourneyEvidenceTracker tracker;
    private final JourneyProgressPolicy progressPolicy;
    public TripMonitoringService(MonitoredTripRepository repository, RouteRiskService risk, RouteGeometry geometry,
            TransactionTemplate transaction, Clock clock, JourneyEvidenceTracker tracker, JourneyProgressPolicy progressPolicy) {
        this.repository = repository; this.risk = risk; this.geometry = geometry; this.transaction = transaction; this.clock = clock; this.tracker = tracker; this.progressPolicy=progressPolicy;
    }
    public MonitoringResponse create(MonitoringRequest request) {
        geometry.validateAndLength(request.route());
        return repository.create(request, clock.instant());
    }
    public MonitoringResponse createGenerated(GeneratedJourneyRequest request) {
        try {
            var points=com.nci.backend.route.provider.EncodedRoute.decode(request.geometry());
            geometry.validateAndLength(new com.nci.backend.route.dto.RouteRiskRequest(points,request.corridorKm(),request.days(),request.minMagnitude()),20000);
        } catch(IllegalArgumentException failure) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
        return repository.createGenerated(request,clock.instant());
    }
    private MonitoringResponse fresh(MonitoringResponse j) {
        return new MonitoringResponse(j.id(),j.name(),j.status(),j.route(),j.createdAt(),j.lastAttemptAt(),j.lastCheckedAt(),j.lastError(),j.assessment(),j.providerCheckpoints(),j.generatedGeometry(),progressPolicy.fresh(j.progress()));
    }
    public MonitoringResponse progress(UUID id,JourneyProgress.Update update) {
        return transaction.execute(tx->{
            var journey=required(id,true);
            if(!journey.status().equals("ACTIVE"))throw new ResponseStatusException(HttpStatus.CONFLICT);
            var next=progressPolicy.match(journey.route(),update);
            if(journey.progress()!=null && !next.observedAt().isAfter(journey.progress().observedAt()))throw new ResponseStatusException(HttpStatus.CONFLICT);
            repository.progress(id,next);return get(id);
        });
    }
    public MonitoringResponse clearProgress(UUID id) {
        return transaction.execute(tx->{required(id,true);repository.progress(id,null);return get(id);});
    }
    public List<MonitoringResponse> list(int page) { return repository.list(offset(page)).stream().map(this::fresh).toList(); }
    public MonitoringResponse get(UUID id) { return required(id, false); }
    private MonitoringResponse required(UUID id, boolean lock) {
        return repository.find(id, lock).map(this::fresh).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public MonitoringResponse status(UUID id, String status) {
        if (!Set.of("ACTIVE", "PAUSED", "COMPLETED", "CANCELLED").contains(status == null ? "" : status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return transaction.execute(tx -> { var old=required(id, true); if(Set.of("COMPLETED","CANCELLED").contains(old.status()) && !old.status().equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT); repository.status(id, status); if(!status.equals("ACTIVE"))repository.progress(id,null); return get(id); });
    }
    public void delete(UUID id) { transaction.executeWithoutResult(tx -> { required(id, true); repository.delete(id); }); }
    public List<JourneyNotice> notices(UUID id, int page) { get(id); return repository.notices(id, offset(page)); }
    public List<MonitoredTripRepository.Revision> revisions(UUID id,int page){get(id);return repository.revisions(id,offset(page));}
    private int offset(int page) {
        if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return page * 50;
    }
    public MonitoringResponse check(UUID id) {
        return transaction.execute(tx -> { required(id,true); return check(id,null); });
    }
    public MonitoringResponse check(UUID id, Instant dueBefore) {
        // Row locking serializes scheduled/manual checks and pause/delete operations across instances.
        return transaction.execute(tx -> {
            var journey = repository.findForMonitoring(id,true).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!journey.status().equals("ACTIVE")) throw new ResponseStatusException(HttpStatus.CONFLICT);
            if (dueBefore != null && journey.lastAttemptAt() != null && journey.lastAttemptAt().isAfter(dueBefore)) return journey;
            var now = clock.instant();
            try {
                int start=progressPolicy.startSegment(journey.progress());
                var request=journey.route();
                if(start>0) {
                    request=new com.nci.backend.route.dto.RouteRiskRequest(request.points().subList(start,request.points().size()),request.corridorKm(),request.days(),request.minMagnitude());
                    // A tiny/duplicate terminal segment is not a valid assessment route; retain full coverage.
                    try {geometry.validateAndLength(request,20000);}catch(ResponseStatusException tooShort){start=0;request=journey.route();}
                }
                var assessment = journey.generatedGeometry()==null && start==0?risk.assessMonitored(request):risk.assessMonitoredGenerated(request);
                if(start>0) {
                    final int offset=start;var a=assessment;
                    assessment=new com.nci.backend.route.dto.RouteRiskResponse(a.modelVersion(),a.query(),a.assessedAt(),a.lengthKm(),a.overallRisk(),a.truncated(),
                        a.matches().stream().map(m->new com.nci.backend.route.dto.RouteRiskResponse.Match(m.event(),m.distanceKm(),m.segmentIndex()+offset)).toList(),
                        a.sources(),"Remaining route from original segment "+(start+1)+"; the entire matched segment is retained conservatively. "+a.explanation(),a.coverageStatus(),a.providerStatuses());
                }
                tracker.apply(journey,assessment,now,start>0);
                boolean complete = assessment.coverageStatus() == com.nci.backend.provider.model.CoverageStatus.COMPLETE;
                repository.checked(id,now,complete ? now : journey.lastCheckedAt(),complete ? null : "INCOMPLETE_COVERAGE",assessment);
            } catch (ProviderException failure) {
                repository.checked(id, now, journey.lastCheckedAt(), failure.getStatusCode().value() == 504 ? "PROVIDER_TIMEOUT" : "PROVIDER_UNAVAILABLE", journey.assessment());
            }
            return fresh(repository.findForMonitoring(id,false).orElseThrow());
        });
    }
}
