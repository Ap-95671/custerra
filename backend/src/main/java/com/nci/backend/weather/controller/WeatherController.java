package com.nci.backend.weather.controller;
import com.nci.backend.weather.dto.WeatherResponse;
import com.nci.backend.weather.service.WeatherService;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/weather/location")
public class WeatherController {
    private final WeatherService service;
    public WeatherController(WeatherService service) { this.service = service; }
    @GetMapping
    public WeatherResponse location(@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude) {
        return service.location(latitude, longitude);
    }
}
