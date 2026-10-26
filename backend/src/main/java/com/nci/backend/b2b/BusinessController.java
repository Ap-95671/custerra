package com.nci.backend.b2b;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.risk.service.RiskService;
import com.nci.backend.risk.dto.RiskResponse;
import com.nci.backend.route.service.RouteRiskService;
import com.nci.backend.route.dto.*;
@RestController @RequestMapping("/api/v1/b2b")
public class BusinessController {
 private final NearbyHazardService nearby;private final RiskService risk;private final RouteRiskService routes;
 public BusinessController(NearbyHazardService nearby,RiskService risk,RouteRiskService routes){this.nearby=nearby;this.risk=risk;this.routes=routes;}
 public record Envelope<T>(String apiVersion,String calculationVersion,String scope,String coverageStatus,String confidence,java.util.List<String> limitations,T data) {}
 private <T> Envelope<T> envelope(T data,String version,String coverage){return new Envelope<>("v1",version,"USGS_EARTHQUAKE_OBSERVATIONS_ONLY",coverage,"UNKNOWN",java.util.List.of("Coverage describes the selected source query, not all real hazards.","No prediction, road-closure guarantee or travel-safety determination.","Other hazard families and historical account data are not exposed."),data);}
 @GetMapping("/hazards/nearby") public Envelope<NearbyHazardResponse> nearby(@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,@RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,@RequestParam(defaultValue="100") @DecimalMin("1") @DecimalMax("1000") double radiusKm,@RequestParam(defaultValue="7") @Min(1) @Max(30) int days){var data=nearby.earthquakes(latitude,longitude,radiusKm,days);return envelope(data,"normalized-evidence-v1",data.coverageStatus().name());}
 @GetMapping("/risk/location") public Envelope<RiskResponse> risk(@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,@RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,@RequestParam(defaultValue="100") @DecimalMin("1") @DecimalMax("1000") double radiusKm,@RequestParam(defaultValue="7") @Min(1) @Max(30) int days){var data=risk.assess(latitude,longitude,radiusKm,days);return envelope(data,data.modelVersion(),data.sourceResultsTruncated()?"TRUNCATED":"COMPLETE");}
 @PostMapping("/risk/route") public Envelope<RouteRiskResponse> route(@Valid @RequestBody RouteRiskRequest request){var data=routes.assess(request);return envelope(data,data.modelVersion(),data.coverageStatus().name());}
}
