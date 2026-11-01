package com.nci.backend.operations;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/internal/operations")
public class OperationsController {
    private final ProviderTelemetry telemetry;
    public OperationsController(ProviderTelemetry telemetry,WorkloadStatus workload,WorkerExecutionStatus executions){this.telemetry=telemetry;this.workload=workload;this.executions=executions;}
    @GetMapping("/providers") public java.util.List<ProviderTelemetry.State> providers(){return telemetry.snapshot();}
    @GetMapping("/workload") public WorkloadStatus.Snapshot workload(){return workload.snapshot();}
    private final WorkloadStatus workload;
    @GetMapping("/workers") public java.util.List<java.util.Map<String,Object>> workers(){return executions.snapshot();}
    private final WorkerExecutionStatus executions;
}
