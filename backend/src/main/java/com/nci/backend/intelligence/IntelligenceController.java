package com.nci.backend.intelligence;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.nci.backend.route.dto.RoutePoint;
@RestController @RequestMapping("/api/v1/intelligence")
public class IntelligenceController {
    private final IntelligenceService service;
    public IntelligenceController(IntelligenceService service){this.service=service;}
    @GetMapping("/nearby") public IntelligenceService.Response nearby(@RequestParam @DecimalMin("-90") @DecimalMax("90")double latitude,@RequestParam @DecimalMin("-180") @DecimalMax("180")double longitude,@RequestParam(defaultValue="100")@Min(1)@Max(1000)double radiusKm,@RequestParam(defaultValue="7")@Min(1)@Max(30)int days){return service.nearby(latitude,longitude,radiusKm,days);}
    @GetMapping("/timeline") public IntelligenceService.Response timeline(){return service.timeline();}
    public record RouteRequest(@Size(max=200000)String geometry,@Size(min=2,max=20000)List<@NotNull @Valid RoutePoint> points){}
    @PostMapping("/route") public IntelligenceService.RouteExposure route(@Valid @RequestBody RouteRequest request){return service.route(service.points(request.geometry(),request.points()));}
    @PostMapping("/explain") public IntelligenceService.Explanation explain(@RequestBody Map<String,String> request){String source=request.get("source"),id=request.get("id");if(id==null||id.length()>500)throw new IllegalArgumentException("Invalid identity");return service.timeline().items().stream().filter(i->i.event().source().equals(source)&&i.event().id().equals(id)).findFirst().map(i->IntelligenceService.explain(i.event(),i.state())).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));}
}
