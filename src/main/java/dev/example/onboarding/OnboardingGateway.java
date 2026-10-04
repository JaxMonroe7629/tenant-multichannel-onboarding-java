package dev.example.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OnboardingGateway implements TenantOnboarding.Gateway {
    private final InfraiClient client;
    public OnboardingGateway(InfraiClient client) { this.client = client; }

    public JsonNode createUser(TenantOnboarding.Signup signup) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", signup.email());
        body.put("password", signup.password());
        body.put("name", signup.adminName());
        body.put("metadata", Map.of("tenant_id", signup.tenantId()));
        body.put("idempotency_key", signup.requestId());
        return client.post("/v1/auth/user/create", body);
    }

    public boolean emailSuppressed(String email) {
        return client.get("/v1/email/suppression/check/" + InfraiClient.segment(email)).path("suppressed").asBoolean(false);
    }

    public boolean smsSuppressed(String phone) {
        return client.post("/v1/sms/suppression/check", Map.of("phone", phone)).path("suppressed").asBoolean(false);
    }

    public JsonNode sendEmail(String email, String tenant) {
        return client.post("/v1/email/send", Map.of("to", email, "subject", "Welcome to " + tenant,
            "body", "Your tenant " + tenant + " is ready."));
    }

    public JsonNode sendSms(String phone, String tenant) {
        return client.post("/v1/sms/send", Map.of("to", phone, "body", "Your tenant " + tenant + " is ready."));
    }
}
