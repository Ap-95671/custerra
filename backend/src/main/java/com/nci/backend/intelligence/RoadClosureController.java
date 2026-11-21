package com.nci.backend.intelligence;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intelligence/road-closures")
public class RoadClosureController {
    private final RoadClosures closures;
    private final IntelligenceService intelligence;

    public RoadClosureController(RoadClosures closures, IntelligenceService intelligence) {
        this.closures = closures;
        this.intelligence = intelligence;
    }

    @PostMapping
    public RoadClosures.Report review(@Valid @RequestBody IntelligenceController.RouteRequest route) {
        return closures.near(intelligence.points(route.geometry(), route.points()));
    }
}
