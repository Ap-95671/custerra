package com.nci.backend.hazard.dto;

import com.nci.backend.hazard.model.*;
import java.time.Instant;
import java.util.UUID;

public record HazardEventResponse(
        UUID id, String externalId, HazardType hazardType, String title, String description,
        SeverityLevel severity, InformationType informationType, double latitude, double longitude,
        Double affectedRadiusKm, Instant startedAt, Instant endedAt, String source,
        String sourceUrl, Instant observedAt, ConfidenceLevel confidence) {}
