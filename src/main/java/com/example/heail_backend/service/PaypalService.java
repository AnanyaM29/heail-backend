package com.example.heail_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to PayPal's REST API directly (Checkout Orders v2 + OAuth2 + webhook
 * signature verification) — PayPal's old SDKs are deprecated, direct REST calls
 * are what PayPal itself recommends now.
 */
@Slf4j
@Service
public class PaypalService {

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.client-secret}")
    private String clientSecret;

    @Value("${paypal.webhook-id}")
    private String webhookId;

    @Value("${paypal.api-base}")
    private String apiBase;

    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    private RestClient client() {
        return RestClient.builder().baseUrl(apiBase).build();
    }

    /** OAuth2 client-credentials grant, cached until just before expiry. */
    private synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        Map<?, ?> response = client().post()
                .uri("/v1/oauth2/token")
                .headers(h -> h.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(Map.class);

        cachedToken = (String) response.get("access_token");
        int expiresIn = ((Number) response.get("expires_in")).intValue();
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn - 60, 30));
        return cachedToken;
    }

    /** Creates a PayPal order (intent=CAPTURE) for the given amount; returns the PayPal order ID. */
    public String createOrder(BigDecimal total, String currency, String reference) {
        Map<String, Object> amount = Map.of(
                "currency_code", currency,
                "value", total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
        );
        Map<String, Object> purchaseUnit = new LinkedHashMap<>();
        purchaseUnit.put("reference_id", reference);
        purchaseUnit.put("amount", amount);

        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(purchaseUnit)
        );

        Map<?, ?> response = client().post()
                .uri("/v2/checkout/orders")
                .headers(h -> h.setBearerAuth(getAccessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return (String) response.get("id");
    }

    /** Captures a previously-approved PayPal order. Returns {status, captureId}. */
    public PaypalCaptureResult captureOrder(String paypalOrderId) {
        Map<?, ?> response = client().post()
                .uri("/v2/checkout/orders/{id}/capture", paypalOrderId)
                .headers(h -> h.setBearerAuth(getAccessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(Map.class);

        String status = (String) response.get("status");
        String captureId = extractCaptureId(response);
        return new PaypalCaptureResult(status, captureId);
    }

    @SuppressWarnings("unchecked")
    private String extractCaptureId(Map<?, ?> orderResponse) {
        try {
            List<Map<String, Object>> purchaseUnits = (List<Map<String, Object>>) orderResponse.get("purchase_units");
            Map<String, Object> payments = (Map<String, Object>) purchaseUnits.get(0).get("payments");
            List<Map<String, Object>> captures = (List<Map<String, Object>>) payments.get("captures");
            return (String) captures.get(0).get("id");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verifies an inbound webhook using PayPal's own verify-webhook-signature endpoint
     * (avoids having to implement certificate-chain verification by hand).
     */
    public boolean verifyWebhookSignature(HttpHeaders headers, String rawBody) {
        try {
            JsonNode eventBody = mapper.readTree(rawBody);

            Map<String, Object> verifyRequest = new LinkedHashMap<>();
            verifyRequest.put("auth_algo", headers.getFirst("paypal-auth-algo"));
            verifyRequest.put("cert_url", headers.getFirst("paypal-cert-url"));
            verifyRequest.put("transmission_id", headers.getFirst("paypal-transmission-id"));
            verifyRequest.put("transmission_sig", headers.getFirst("paypal-transmission-sig"));
            verifyRequest.put("transmission_time", headers.getFirst("paypal-transmission-time"));
            verifyRequest.put("webhook_id", webhookId);
            verifyRequest.put("webhook_event", eventBody);

            Map<?, ?> response = client().post()
                    .uri("/v1/notifications/verify-webhook-signature")
                    .headers(h -> h.setBearerAuth(getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(verifyRequest)
                    .retrieve()
                    .body(Map.class);

            return "SUCCESS".equals(response.get("verification_status"));
        } catch (Exception e) {
            log.error("PayPal webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    public record PaypalCaptureResult(String status, String captureId) {
        public boolean completed() { return "COMPLETED".equals(status); }
    }
}
