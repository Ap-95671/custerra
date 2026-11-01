package com.nci.backend.monitoring.controller;
import java.util.*;
import com.nci.backend.monitoring.dto.*;
import com.nci.backend.monitoring.service.TripMonitoringService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/v1/journeys")
public class MonitoringController {
    private final TripMonitoringService service;
    private final boolean schedulerEnabled;
    private final long intervalMs;
    public MonitoringController(TripMonitoringService service,
            @org.springframework.beans.factory.annotation.Value("${custerra.monitoring.enabled:false}") boolean schedulerEnabled,
            @org.springframework.beans.factory.annotation.Value("${custerra.monitoring.interval-ms:300000}") long intervalMs) {
        this.service = service; this.schedulerEnabled = schedulerEnabled; this.intervalMs = intervalMs;
    }
    public record Configuration(boolean schedulerEnabled, long intervalMs) {}
    @GetMapping("/monitoring") public Configuration configuration() { return new Configuration(schedulerEnabled, intervalMs); }
    public record StatusRequest(@NotNull String status) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MonitoringResponse create(@Valid @RequestBody MonitoringRequest request) { return service.create(request); }
    @PostMapping("/generated") @ResponseStatus(HttpStatus.CREATED)
    public MonitoringResponse generated(@Valid @RequestBody GeneratedJourneyRequest request) { return service.createGenerated(request); }
    @PutMapping("/{id}/progress")
    public MonitoringResponse progress(@PathVariable UUID id,@Valid @RequestBody JourneyProgress.Update request) { return service.progress(id,request); }
    @DeleteMapping("/{id}/progress")
    public MonitoringResponse clearProgress(@PathVariable UUID id) { return service.clearProgress(id); }
    @GetMapping public List<MonitoringResponse> list(@RequestParam(defaultValue="0") int page) { return service.list(page); }
    @GetMapping("/{id}") public MonitoringResponse get(@PathVariable UUID id) { return service.get(id); }
    @PatchMapping("/{id}") public MonitoringResponse status(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) { return service.status(id, request.status()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
    @PostMapping("/{id}/check") public MonitoringResponse check(@PathVariable UUID id) { return service.check(id); }
    @GetMapping("/{id}/evidence-history") public List<com.nci.backend.monitoring.repository.MonitoredTripRepository.Revision> revisions(@PathVariable UUID id,@RequestParam(defaultValue="0") int page){return service.revisions(id,page);}
    @GetMapping("/{id}/notices") public List<JourneyNotice> notices(@PathVariable UUID id, @RequestParam(defaultValue="0") int page) { return service.notices(id, page); }
}
