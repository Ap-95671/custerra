package com.nci.backend.route.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.nci.backend.route.provider.RouteProvider.TravelMode;

public record RoutePlanRequest(@NotNull @Valid RoutePoint origin, @NotNull @Valid RoutePoint destination,
        @NotNull TravelMode mode,
        @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @DecimalMin("1") @DecimalMax("100") double corridorKm,
        @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @Min(1) @Max(30) int days,
        @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @DecimalMin("0") @DecimalMax("10") double minMagnitude) {}
