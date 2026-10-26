package com.nci.backend.monitoring.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.nci.backend.route.dto.RouteRiskRequest;
public record MonitoringRequest(@NotBlank @Size(max=120) String name, @NotNull @Valid RouteRiskRequest route) {}
