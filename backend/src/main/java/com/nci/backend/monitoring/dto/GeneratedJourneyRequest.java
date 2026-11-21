package com.nci.backend.monitoring.dto;
import jakarta.validation.constraints.*;
public record GeneratedJourneyRequest(@NotBlank @Size(max=120) String name,
        @NotBlank @Size(max=120) String originName, @NotBlank @Size(max=120) String destinationName,
        @NotBlank @Size(max=200000) String geometry,
        @NotNull @DecimalMin("1") @DecimalMax("100") Double corridorKm,
        @NotNull @Min(1) @Max(30) Integer days,
        @NotNull @DecimalMin("0") @DecimalMax("10") Double minMagnitude) {}
