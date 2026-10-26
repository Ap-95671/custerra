package com.nci.backend.route.controller;

import com.nci.backend.route.dto.*;
import com.nci.backend.route.provider.GeocodingProvider.Places;
import com.nci.backend.route.service.RoutePlanningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RoutePlanningController {
    private final RoutePlanningService service;
    public RoutePlanningController(RoutePlanningService service) { this.service=service; }
    @GetMapping("/geocoding/search")
    public Places search(@RequestParam @NotBlank @Size(min=2,max=120) String q) { return service.search(q); }
    @PostMapping("/routes/plan")
    public PlannedRouteResponse plan(@Valid @RequestBody RoutePlanRequest request) { return service.plan(request); }
}
