package dev.example.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TenantOnboardingTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void suppressedSignupChannelFallsBackWithoutCreatingTwoAccounts() throws Exception {
        class Stub implements TenantOnboarding.Gateway {
            int accounts, emails, texts;
            public JsonNode createUser(TenantOnboarding.Signup s) { accounts++; return json.createObjectNode().put("user_id", "u-8"); }
            public boolean emailSuppressed(String email) { return true; }
            public boolean smsSuppressed(String phone) { return false; }
            public JsonNode sendEmail(String email, String tenant) { emails++; return json.createObjectNode(); }
            public JsonNode sendSms(String phone, String tenant) { texts++; return json.createObjectNode().put("message_id", "m-8"); }
        }
        Stub stub = new Stub();
        TenantOnboarding service = new TenantOnboarding(stub);
        var signup = new TenantOnboarding.Signup("ledger-42", "request-42", "admin@example.org", "+15551234567", "secure-pass", "Ada", TenantOnboarding.Channel.EMAIL);
        var result = service.onboard(signup);
        assertEquals(TenantOnboarding.Channel.SMS, result.deliveredBy());
        assertEquals("ACTIVE", result.state());
        assertEquals("m-8", result.messageId());
        assertEquals(result, service.onboard(signup));
        assertEquals(1, stub.accounts);
        assertEquals(0, stub.emails);
        assertEquals(1, stub.texts);
    }
}
