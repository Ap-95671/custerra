package com.nci.backend.hazard.controller;

import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.service.NearbyHazardService;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hazards/nearby")
public class NearbyHazardController {
    private final NearbyHazardService service;
    public NearbyHazardController(NearbyHazardService service) { this.service = service; }

    @GetMapping
    public NearbyHazardResponse nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam(defaultValue = "100") @DecimalMin("1") @DecimalMax("1000") double radiusKm,
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days,
            @RequestParam(required = false) com.nci.backend.hazard.model.HazardType hazardType,
            @RequestParam(required = false) @Pattern(regexp = "USGS|FIRMS|GDACS") String provider) {
        return hazardType == null && provider == null ? service.nearby(latitude, longitude, radiusKm, days)
                : service.nearby(latitude, longitude, radiusKm, days, hazardType, provider);
    }
}
