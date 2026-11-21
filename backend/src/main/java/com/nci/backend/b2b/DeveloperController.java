package com.nci.backend.b2b;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import com.nci.backend.auth.CurrentAccount;
@RestController @RequestMapping("/api/v1/developer")
public class DeveloperController {
 private final ApiClientService service;private final CurrentAccount account;
 public DeveloperController(ApiClientService service,CurrentAccount account){this.service=service;this.account=account;}
 public record Create(@NotBlank @Size(max=80) String name,@Size(min=1,max=3) Set<@NotNull String> scopes) {}
 @PostMapping("/clients") @ResponseStatus(HttpStatus.CREATED) public ApiClientService.Issued create(@Valid @RequestBody Create request,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.create(account.id(),request.name(),request.scopes());}
 @GetMapping("/clients") public List<ApiClientService.Client> list(){return service.list(account.id());}
 @DeleteMapping("/clients/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revoke(@PathVariable UUID id){service.revoke(account.id(),id);}
 @PostMapping("/clients/{id}/rotate") public ApiClientService.Issued rotate(@PathVariable UUID id,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.rotate(account.id(),id);}
 @GetMapping("/usage") public ApiClientService.Usage usage(){return service.usage(account.id());}
}
