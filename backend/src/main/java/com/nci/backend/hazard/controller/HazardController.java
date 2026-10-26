package com.nci.backend.hazard.controller;

import com.nci.backend.hazard.dto.HazardEventResponse;
import com.nci.backend.hazard.dto.HazardPageResponse;
import com.nci.backend.hazard.model.HazardType;
import com.nci.backend.hazard.model.SeverityLevel;
import com.nci.backend.hazard.service.HazardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hazards")
public class HazardController {
    private final HazardService service;

    public HazardController(HazardService service) {
        this.service = service;
    }

    @GetMapping
    public HazardPageResponse list(@RequestParam(required = false) HazardType hazardType,
            @RequestParam(required = false) SeverityLevel severity,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.findHazards(hazardType, severity, page, size);
    }

    @GetMapping("/{id}")
    public HazardEventResponse get(@PathVariable UUID id) {
        return service.findById(id);
    }
}
