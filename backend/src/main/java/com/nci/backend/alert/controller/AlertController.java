package com.nci.backend.alert.controller;
import java.time.Clock;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.nci.backend.alert.dto.*;
import com.nci.backend.alert.repository.*;
import com.nci.backend.alert.provider.PushGateway;
import com.nci.backend.auth.CurrentAccount;
@RestController
@RequestMapping("/api/v1/notifications")
public class AlertController {
    private final AlertRepository alerts;
    private final PushSubscriptionRepository subscriptions;
    private final PushGateway push;
    private final CurrentAccount current;
    private final Clock clock;
    private final boolean dispatcherEnabled;
    public AlertController(AlertRepository alerts,PushSubscriptionRepository subscriptions,PushGateway push,CurrentAccount current,Clock clock,
            @org.springframework.beans.factory.annotation.Value("${custerra.notifications.enabled:false}") boolean dispatcherEnabled) {
        this.alerts=alerts;this.subscriptions=subscriptions;this.push=push;this.current=current;this.clock=clock;this.dispatcherEnabled=dispatcherEnabled;
    }
    @GetMapping public List<AlertResponse> list(@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page) { return alerts.list(current.id(),page*50); }
    @GetMapping("/{id}") public AlertResponse get(@PathVariable UUID id) { return alerts.find(current.id(),id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    @PostMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@PathVariable UUID id) {
        if(!alerts.markRead(current.id(),id,clock.instant())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @GetMapping("/preferences") public NotificationPreference preferences() { return alerts.preferences(current.id()); }
    @PatchMapping("/preferences") public NotificationPreference preferences(@Valid @RequestBody NotificationPreference p) { alerts.preferences(current.id(),p);return p; }
    public record PushConfiguration(boolean configured,boolean dispatcherEnabled,String publicKey) {}
    @GetMapping("/push") public PushConfiguration push() { return new PushConfiguration(push.configured(),dispatcherEnabled,push.publicKey()); }
    public record SubscriptionRequest(@NotBlank @Size(max=2048) String endpoint,@NotBlank @Size(max=100) String p256dh,@NotBlank @Size(max=40) String auth) {
        @Override public String toString() { return "SubscriptionRequest[redacted]"; }
    }
    @PostMapping("/subscriptions") @ResponseStatus(HttpStatus.CREATED) public Map<String,UUID> subscribe(@Valid @RequestBody SubscriptionRequest request,HttpSession session) {
        if(!push.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        UUID id=subscriptions.save(current.id(),request.endpoint(),request.p256dh(),request.auth());session.setAttribute(PushSubscriptionRepository.SESSION_ATTRIBUTE,id);return Map.of("id",id);
    }
    public record EndpointRequest(@NotBlank @Size(max=2048) String endpoint) { @Override public String toString() { return "EndpointRequest[redacted]"; } }
    @PostMapping("/subscriptions/remove") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@Valid @RequestBody EndpointRequest request) { subscriptions.remove(current.id(),request.endpoint()); }
}
