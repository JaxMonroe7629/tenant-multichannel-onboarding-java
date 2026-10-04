package dev.example.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TenantOnboarding {
    public record Signup(String tenantId, String requestId, String email, String phone, String password, String adminName, Channel channel) {}
    public enum Channel { EMAIL, SMS }
    public record Receipt(String tenantId, String userId, String state, Channel deliveredBy, String messageId) {}
    public interface Gateway {
        JsonNode createUser(Signup signup);
        boolean emailSuppressed(String email);
        boolean smsSuppressed(String phone);
        JsonNode sendEmail(String email, String tenant);
        JsonNode sendSms(String phone, String tenant);
    }

    private final Gateway gateway;
    private final Map<String, Receipt> tenants = new ConcurrentHashMap<>();
    private final Map<String, Signup> requests = new ConcurrentHashMap<>();

    public TenantOnboarding(Gateway gateway) { this.gateway = gateway; }

    public synchronized Receipt onboard(Signup signup) {
        if (signup.tenantId() == null || signup.tenantId().isBlank() || signup.requestId() == null || signup.requestId().isBlank()
            || signup.email() == null || signup.email().isBlank() || signup.password() == null || signup.password().isBlank()
            || signup.channel() == null) throw new IllegalArgumentException("tenantId, requestId, email, password and channel are required");
        Receipt previous = tenants.get(signup.tenantId());
        if (previous != null) {
            if (!requests.get(signup.tenantId()).equals(signup)) throw new IllegalArgumentException("Signup request mismatch");
            return previous;
        }

        Channel selected = choose(signup.channel(), gateway.emailSuppressed(signup.email()),
            signup.phone() == null || signup.phone().isBlank() || gateway.smsSuppressed(signup.phone()));
        JsonNode user = gateway.createUser(signup);
        String userId = user.path("user_id").asText(user.path("id").asText());
        if (userId.isBlank()) throw new IllegalStateException("Missing user identifier");
        Receipt pending = new Receipt(signup.tenantId(), userId, "PENDING_WELCOME", selected, null);
        requests.put(signup.tenantId(), signup);
        tenants.put(signup.tenantId(), pending);
        return deliver(signup, pending);
    }

    public synchronized Receipt retryWelcome(Signup signup) {
        Receipt receipt = tenants.get(signup.tenantId());
        if (receipt == null) throw new IllegalArgumentException("Unknown tenant");
        if (!requests.get(signup.tenantId()).equals(signup)) throw new IllegalArgumentException("Signup request mismatch");
        if (!receipt.state().equals("PENDING_WELCOME")) return receipt;
        return deliver(signup, receipt);
    }

    public Receipt status(String tenantId) {
        Receipt receipt = tenants.get(tenantId);
        if (receipt == null) throw new IllegalArgumentException("Unknown tenant");
        return receipt;
    }

    private Receipt deliver(Signup signup, Receipt pending) {
        JsonNode sent = pending.deliveredBy() == Channel.EMAIL
            ? gateway.sendEmail(signup.email(), signup.tenantId())
            : gateway.sendSms(signup.phone(), signup.tenantId());
        Receipt done = new Receipt(pending.tenantId(), pending.userId(), "ACTIVE", pending.deliveredBy(),
            sent.path("message_id").asText(null));
        tenants.put(signup.tenantId(), done);
        return done;
    }

    public static Channel choose(Channel preferred, boolean emailSuppressed, boolean smsSuppressed) {
        if (preferred == Channel.EMAIL && !emailSuppressed) return Channel.EMAIL;
        if (preferred == Channel.SMS && !smsSuppressed) return Channel.SMS;
        if (!emailSuppressed) return Channel.EMAIL;
        if (!smsSuppressed) return Channel.SMS;
        throw new IllegalArgumentException("No deliverable channel");
    }
}
