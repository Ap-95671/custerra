package com.nci.backend.monitoring.dto;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.nci.backend.route.dto.RoutePoint;
public record JourneyProgress(RoutePoint position, Instant observedAt, Double accuracyMeters,
        int segmentIndex, double completedFraction, double distanceFromRouteKm, String state) {
    public record Update(@NotNull @Valid RoutePoint position, @NotNull Instant observedAt,
                         @NotNull @DecimalMin("0") @DecimalMax("100000") Double accuracyMeters) {}
    @Override public String toString() { return "JourneyProgress[private position]"; }
}
