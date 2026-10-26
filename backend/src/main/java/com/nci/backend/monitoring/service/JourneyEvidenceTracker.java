package com.nci.backend.monitoring.service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.nci.backend.monitoring.dto.*;
import com.nci.backend.monitoring.repository.MonitoredTripRepository;
import com.nci.backend.route.dto.RouteRiskResponse;
import com.nci.backend.provider.model.*;

/** Called under the journey row lock and transaction; positive evidence and completeness are separate. */
@Service
public class JourneyEvidenceTracker {
    private final MonitoredTripRepository repository;
    private final ObjectMapper json;
    private final JourneyNoticeService notices;
    public JourneyEvidenceTracker(MonitoredTripRepository repository, ObjectMapper json, JourneyNoticeService notices) { this.repository=repository; this.json=json; this.notices=notices; }
    public void apply(MonitoringResponse journey, RouteRiskResponse assessment, Instant now) {
        var statuses = assessment.providerStatuses().isEmpty()
                ? List.of(new ProviderStatus("USGS",assessment.coverageStatus(),assessment.assessedAt(),List.of())) : assessment.providerStatuses();
        for (var status : statuses) {
            if (status.coverageStatus() != CoverageStatus.COMPLETE && status.coverageStatus() != CoverageStatus.PARTIAL) continue;
            boolean baseline = journey.providerCheckpoints().stream().anyMatch(c -> c.provider().equals(status.provider()));
            var previous = repository.states(journey.id(),status.provider());
            var current = new HashSet<String>();
            for (var match : assessment.matches()) {
                var event = match.event();
                if (!event.source().equals(status.provider())) continue;
                current.add(event.externalId());
                var old = previous.get(event.externalId());
                // Do not let an older feed revision reverse a newer state.
                if (old != null && old.evidence() != null && old.evidence().event().sourceUpdatedAt() != null
                        && event.sourceUpdatedAt() != null && event.sourceUpdatedAt().isBefore(old.evidence().event().sourceUpdatedAt())) continue;
                String fingerprint = fingerprint(match);
                boolean changed = old == null || !old.present() || (old.fingerprint() != null && !old.fingerprint().equals(fingerprint));
                long version = old == null ? 1 : old.version() + (changed ? 1 : 0);
                repository.remember(journey.id(),match,fingerprint,version,true);
                if (baseline && changed) {
                    String type = old == null ? "NEW_MATCHING_OBSERVATION" : old.present() ? "UPDATED_MATCHING_EVIDENCE" : "MATCHING_EVIDENCE_RETURNED";
                    notice(journey.id(),now,type,match,version,
                            "A " + event.hazardType() + " " + event.informationType() + " from " + event.source()
                            + " now has " + (old == null ? "newly retrieved" : "changed") + " evidence near route segment " + (match.segmentIndex()+1)
                            + ". Thermal anomalies are not confirmed wildfires. Provider revisions may arrive late. This is informational, not an official warning or a measured change in risk.");
                }
            }
            // Partial feeds can establish presence, never disappearance or hazard resolution.
            if (status.coverageStatus() == CoverageStatus.COMPLETE) for (var old : previous.values()) {
                if (!old.present() || old.evidence() == null || current.contains(old.externalId())) continue;
                repository.remember(journey.id(),old.evidence(),old.fingerprint(),old.version()+1,false);
                if (baseline) notice(journey.id(),now,"NO_LONGER_MATCHING",old.evidence(),old.version()+1,
                        "Previously retrieved evidence no longer matches this complete provider query. It may have aged outside the lookback window, moved, or been revised or removed. This does not confirm that the hazard has ended or that the route is safe.");
            }
            repository.checkpoint(journey.id(),status.provider(),now);
        }
    }
    private void notice(UUID journey, Instant now, String type, RouteRiskResponse.Match match, long version, String explanation) {
        notices.create(new JourneyNotice(UUID.randomUUID(),journey,now,type,match,explanation,version));
    }
    private String fingerprint(RouteRiskResponse.Match match) {
        var e = match.event();
        // Retrieval/update timestamps and editorial titles do not constitute meaningful exposure changes.
        var fields = new TreeMap<String,Object>();
        fields.put("hazard",e.hazardType()); fields.put("information",e.informationType()); fields.put("severity",e.severity());
        fields.put("latitude",e.latitude()); fields.put("longitude",e.longitude()); fields.put("radius",e.affectedRadiusKm());
        fields.put("confidence",e.confidence()); fields.put("start",e.startedAt()); fields.put("end",e.endedAt());
        fields.put("measurements",new TreeMap<>(e.measurements()));
        var area = new TreeMap<String,String>();
        e.attributes().forEach((key,value) -> { if (key.toLowerCase(Locale.ROOT).contains("bbox") || key.equals("alertLevel")) area.put(key,value); });
        fields.put("area",area);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
