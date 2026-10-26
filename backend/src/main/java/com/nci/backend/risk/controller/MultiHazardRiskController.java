package com.nci.backend.risk.controller;

import com.nci.backend.risk.dto.MultiHazardRiskResponse;
import com.nci.backend.risk.service.MultiHazardRiskService;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/risk/multi-hazard")
public class MultiHazardRiskController {
    private final MultiHazardRiskService service;
    public MultiHazardRiskController(MultiHazardRiskService service) { this.service=service; }
    @GetMapping public MultiHazardRiskResponse assess(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam(defaultValue="100") @DecimalMin("1") @DecimalMax("1000") double radiusKm,
            @RequestParam(defaultValue="7") @Min(1) @Max(30) int days) {
        return service.assess(latitude,longitude,radiusKm,days);
    }
}
