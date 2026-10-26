package com.nci.backend.provider.model;

import java.time.Instant;

public record HazardQuery(double latitude, double longitude, double radiusKm, Instant from, Instant to) {
    public HazardQuery {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(radiusKm) || radiusKm < 1 || radiusKm > 1000
                || from == null || to == null || !from.isBefore(to)
                || java.time.Duration.between(from, to).compareTo(java.time.Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("Invalid hazard query");
        }
    }
}
