package com.nci.backend.route.dto;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
public record RouteRiskRequest(@NotNull @Size(min=2, max=20) List<@NotNull @Valid RoutePoint> points,
        @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @DecimalMin("1") @DecimalMax("100") double corridorKm,
        @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @Min(1) @Max(30) int days, @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @DecimalMin("0") @DecimalMax("10") double minMagnitude) {
    public RouteRiskRequest { if (points != null) points = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(points)); }
}
