package com.nci.backend.operations;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/internal/operations")
public class OperationsController {
    private final ProviderTelemetry telemetry;
    public OperationsController(ProviderTelemetry telemetry){this.telemetry=telemetry;}
    @GetMapping("/providers") public java.util.List<ProviderTelemetry.State> providers(){return telemetry.snapshot();}
}
