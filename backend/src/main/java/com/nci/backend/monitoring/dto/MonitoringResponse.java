package com.nci.backend.monitoring.dto;
import java.time.Instant;
import java.util.UUID;
import com.nci.backend.route.dto.*;
public record MonitoringResponse(UUID id, String name, String status, RouteRiskRequest route,
        Instant createdAt, Instant lastAttemptAt, Instant lastCheckedAt, String lastError, RouteRiskResponse assessment, java.util.List<ProviderCheckpoint> providerCheckpoints,
        GeneratedGeometry generatedGeometry, JourneyProgress progress) {
    public MonitoringResponse(UUID id,String name,String status,RouteRiskRequest route,Instant createdAt,Instant lastAttemptAt,
            Instant lastCheckedAt,String lastError,RouteRiskResponse assessment,java.util.List<ProviderCheckpoint> providerCheckpoints) {
        this(id,name,status,route,createdAt,lastAttemptAt,lastCheckedAt,lastError,assessment,providerCheckpoints,null,null);
    }
    public record GeneratedGeometry(String encodedPolyline, int version, String originName, String destinationName) {}
    public MonitoringResponse { providerCheckpoints = providerCheckpoints == null ? java.util.List.of() : java.util.List.copyOf(providerCheckpoints); }
    public record ProviderCheckpoint(String provider, Instant lastSuccessfulCheck) {}
}
