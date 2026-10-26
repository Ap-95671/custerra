package com.nci.backend.route.dto;
import jakarta.validation.constraints.*;
public record RoutePoint(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
