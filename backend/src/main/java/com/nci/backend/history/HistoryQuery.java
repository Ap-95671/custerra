package com.nci.backend.history;

import java.time.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record HistoryQuery(@JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @DecimalMin("-90") @DecimalMax("90") double latitude,
        @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @DecimalMin("-180") @DecimalMax("180") double longitude,
        @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @DecimalMin("1") @DecimalMax("1000") double radiusKm,
        @NotNull Instant from, @NotNull Instant to,
        @JsonProperty(required=true) @JsonSetter(nulls=Nulls.FAIL) @DecimalMin("0") @DecimalMax("10") double minMagnitude) {
    public void validate(Instant now) {
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90 || !Double.isFinite(longitude)
                || Math.abs(longitude) > 180 || !Double.isFinite(radiusKm) || radiusKm < 1 || radiusKm > 1000
                || !Double.isFinite(minMagnitude) || minMagnitude < 0 || minMagnitude > 10
                || from == null || to == null || !from.isBefore(to) || to.isAfter(now)
                || from.isBefore(Instant.parse("1900-01-01T00:00:00Z"))
                || Duration.between(from, to).compareTo(Duration.ofDays(3660)) > 0
                || Duration.between(from, to).compareTo(Duration.ofDays(1)) < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
}
