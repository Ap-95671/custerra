package com.nci.backend.route.controller;
import com.nci.backend.route.dto.*;
import com.nci.backend.route.service.RouteRiskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {
    private final RouteRiskService service;
    public RouteController(RouteRiskService service) { this.service = service; }
    @PostMapping("/assessment")
    public RouteRiskResponse assess(@Valid @RequestBody RouteRiskRequest query) { return service.assess(query); }
}
