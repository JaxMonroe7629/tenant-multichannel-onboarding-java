package dev.example.onboarding;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenants")
public class TenantController {
    private final TenantOnboarding service;
    public TenantController(TenantOnboarding service) { this.service = service; }

    @PostMapping
    public TenantOnboarding.Receipt onboard(@RequestBody TenantOnboarding.Signup signup) {
        return service.onboard(signup);
    }

    @GetMapping("/{tenantId}")
    public TenantOnboarding.Receipt status(@PathVariable String tenantId) {
        return service.status(tenantId);
    }

    @PostMapping("/{tenantId}/retry-welcome")
    public TenantOnboarding.Receipt retry(@PathVariable String tenantId, @RequestBody TenantOnboarding.Signup signup) {
        if (!tenantId.equals(signup.tenantId())) throw new IllegalArgumentException("Tenant mismatch");
        return service.retryWelcome(signup);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InfraiClient.InfraiFailure.class)
    public ResponseEntity<Map<String, String>> upstream(InfraiClient.InfraiFailure ex) {
        int status = ex.status >= 500 ? 502 : ex.status;
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(Map.of("error", ex.code, "message", ex.getMessage()));
    }
}
