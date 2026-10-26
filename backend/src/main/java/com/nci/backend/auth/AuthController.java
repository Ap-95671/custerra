package com.nci.backend.auth;
import java.util.*;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AccountService accounts;
    private final CurrentAccount current;
    public AuthController(AccountService accounts,CurrentAccount current) { this.accounts=accounts;this.current=current; }
    public record Registration(@NotNull @Pattern(regexp="[a-zA-Z0-9_]{3,40}") String username,
                               @NotNull @Size(min=12,max=72) String password) {
        @Override public String toString() { return "Registration[redacted]"; }
    }
    public record Identity(UUID id,String username) {}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("headerName",token.getHeaderName(),"token",token.getToken()); }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Identity register(@Valid @RequestBody Registration request) {
        return new Identity(accounts.register(request.username(),request.password()),request.username().toLowerCase(Locale.ROOT));
    }
    @GetMapping("/me") public Identity me(Principal principal) { return new Identity(current.id(),principal.getName()); }
}
