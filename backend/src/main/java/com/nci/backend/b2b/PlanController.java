package com.nci.backend.b2b;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/internal/organizations")
public class PlanController {
 private final ApiClientService service;public PlanController(ApiClientService service){this.service=service;}
 public record Plan(@NotNull @Pattern(regexp="DEVELOPMENT|STANDARD") String plan) {}
 @PutMapping("/{id}/plan") public void update(@PathVariable java.util.UUID id,@Valid @RequestBody Plan plan){service.plan(id,plan.plan());}
}
