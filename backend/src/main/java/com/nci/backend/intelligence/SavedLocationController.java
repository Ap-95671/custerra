package com.nci.backend.intelligence;
import java.time.Clock;
import java.util.*;
import com.nci.backend.auth.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/v1/locations")
public class SavedLocationController {
    public record Create(@NotBlank @Size(max=80)String name,@DecimalMin("-90")@DecimalMax("90")@NotNull Double latitude,@DecimalMin("-180")@DecimalMax("180")@NotNull Double longitude,@DecimalMin("1")@DecimalMax("1000")@NotNull Double radiusKm,@Min(1)@Max(30)@NotNull Integer days){}
    public record Monitoring(@NotNull Boolean enabled){}
    private final SavedLocationRepository repository;private final SavedLocationService service;private final CurrentAccount account;private final Clock clock;
    public SavedLocationController(SavedLocationRepository repository,SavedLocationService service,CurrentAccount account,Clock clock){this.repository=repository;this.service=service;this.account=account;this.clock=clock;}
    @GetMapping public List<SavedLocationRepository.Location> list(){return repository.list(account.id());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public SavedLocationRepository.Location create(@Valid @RequestBody Create request){return repository.create(account.id(),request,clock.instant());}
    @GetMapping("/{id}") public SavedLocationRepository.Location get(@PathVariable UUID id){return repository.get(account.id(),id);}
    @PatchMapping("/{id}/monitoring")public SavedLocationRepository.Location monitoring(@PathVariable UUID id,@Valid @RequestBody Monitoring request){return repository.monitoring(account.id(),id,request.enabled());}
    @DeleteMapping("/{id}")@ResponseStatus(HttpStatus.NO_CONTENT)public void delete(@PathVariable UUID id){repository.delete(account.id(),id);}
    @PostMapping("/{id}/check")public SavedLocationService.Check check(@PathVariable UUID id){return service.check(account.id(),id);}
    @GetMapping("/{id}/history")public List<SavedLocationService.Check> history(@PathVariable UUID id){return repository.history(account.id(),id);}
    @GetMapping("/configuration")public Map<String,Object> configuration(){return Map.of("schedulerEnabled",Boolean.parseBoolean(System.getenv().getOrDefault("SAVED_LOCATION_MONITORING_ENABLED","false")),"intervalSeconds",600,"channels",List.of("IN_APP_CHECK_HISTORY"));}
}
