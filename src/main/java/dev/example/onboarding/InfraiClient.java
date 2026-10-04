package dev.example.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;

    public InfraiClient(ObjectMapper json, @Value("${onboarding.infrai-base-url}") String baseUrl) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
    }

    public static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public JsonNode get(String path) { return request("GET", path, null); }
    public JsonNode post(String path, Map<String, ?> body) { return request("POST", path, body); }

    private JsonNode request(String method, String path, Map<String, ?> body) {
        try {
            String payload = body == null ? "" : json.writeValueAsString(body);
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long delay = response.headers().firstValue("Retry-After").map(InfraiClient::retrySeconds).orElse(1L << attempt);
                    Thread.sleep(Math.min(30, delay) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new InfraiFailure(response.statusCode(), error.path("code").asText("INFRAI_ERROR"), error.path("message").asText("Request rejected"));
                }
                if (response.statusCode() >= 500) throw new InfraiFailure(502, "UPSTREAM_ERROR", "Upstream request failed");
                return envelope.path("data");
            }
            throw new InfraiFailure(429, "RATE_LIMITED", "Try again later");
        } catch (InfraiFailure ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InfraiFailure(502, "INTERRUPTED", "Request interrupted");
        } catch (Exception ex) {
            throw new InfraiFailure(502, "TRANSPORT_ERROR", "Upstream request failed");
        }
    }

    private static long retrySeconds(String value) {
        try { return Math.max(1, Long.parseLong(value)); }
        catch (NumberFormatException ex) { return 1; }
    }

    public static class InfraiFailure extends RuntimeException {
        public final int status;
        public final String code;
        public InfraiFailure(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
