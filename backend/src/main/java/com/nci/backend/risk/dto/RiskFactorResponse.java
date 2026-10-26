package com.nci.backend.risk.dto;

import java.time.Instant;

/** The inputs and contribution of one source observation to the experimental indicator. */
public record RiskFactorResponse(String code, String explanation, String source, String externalId,
        String sourceUrl, Instant startedAt, Instant sourceUpdatedAt, double distanceKm,
        Double magnitude, double ageHours, Integer magnitudePoints,
        Double distanceWeight, Double recencyWeight, Integer contribution) {}
