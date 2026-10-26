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
    public TripMonitoringService(MonitoredTripRepository repository, RouteRiskService risk, RouteGeometry geometry,
            TransactionTemplate transaction, Clock clock, JourneyEvidenceTracker tracker) {
        this.repository = repository; this.risk = risk; this.geometry = geometry; this.transaction = transaction; this.clock = clock; this.tracker = tracker;
    }
    public MonitoringResponse create(MonitoringRequest request) {
        geometry.validateAndLength(request.route());
        return repository.create(request, clock.instant());
    }
    public List<MonitoringResponse> list(int page) { return repository.list(offset(page)); }
    public MonitoringResponse get(UUID id) { return required(id, false); }
    private MonitoringResponse required(UUID id, boolean lock) {
        return repository.find(id, lock).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public MonitoringResponse status(UUID id, String status) {
        if (!Set.of("ACTIVE", "PAUSED").contains(status == null ? "" : status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return transaction.execute(tx -> { required(id, true); repository.status(id, status); return get(id); });
    }
    public void delete(UUID id) { transaction.executeWithoutResult(tx -> { required(id, true); repository.delete(id); }); }
    public List<JourneyNotice> notices(UUID id, int page) { get(id); return repository.notices(id, offset(page)); }
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
                var assessment = risk.assessMonitored(journey.route());
                tracker.apply(journey,assessment,now);
                boolean complete = assessment.coverageStatus() == com.nci.backend.provider.model.CoverageStatus.COMPLETE;
                repository.checked(id,now,complete ? now : journey.lastCheckedAt(),complete ? null : "INCOMPLETE_COVERAGE",assessment);
            } catch (ProviderException failure) {
                repository.checked(id, now, journey.lastCheckedAt(), failure.getStatusCode().value() == 504 ? "PROVIDER_TIMEOUT" : "PROVIDER_UNAVAILABLE", journey.assessment());
            }
            return repository.findForMonitoring(id,false).orElseThrow();
        });
    }
}
