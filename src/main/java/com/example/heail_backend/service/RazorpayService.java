package com.example.heail_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Talks to Razorpay's REST API directly (Orders API + HMAC-SHA256 signature
 * verification) — no SDK, to avoid an extra dependency for what's ~3 HTTP
 * calls and an HMAC check.
 */
@Slf4j
@Service
public class RazorpayService {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    @Value("${razorpay.api-base}")
    private String apiBase;

    private RestClient client() {
        return RestClient.builder().baseUrl(apiBase).build();
    }

    /** Key ID is public by design (only the Secret is sensitive) — exposed so the
     *  frontend can read it from the order response instead of keeping its own
     *  separately-configured copy in sync with this one. */
    public String getKeyId() {
        return keyId;
    }

    /** Razorpay's own convention: test keys are always prefixed "rzp_test_", live keys "rzp_live_". */
    public boolean isTestMode() {
        return keyId != null && keyId.startsWith("rzp_test_");
    }

    /** Creates a Razorpay order for the given amount (auto-captured by default); returns the Razorpay order ID. */
    public String createOrder(BigDecimal total, String currency, String receipt) {
        long amountInSmallestUnit = total.setScale(2, java.math.RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();

        Map<String, Object> body = Map.of(
                "amount", amountInSmallestUnit,
                "currency", currency,
                "receipt", receipt
        );

        Map<?, ?> response = client().post()
                .uri("/v1/orders")
                .headers(h -> h.setBasicAuth(keyId, keySecret))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return (String) response.get("id");
    }

    /**
     * Verifies the signature Razorpay Checkout.js hands back to the client after a
     * successful payment: HMAC-SHA256("{order_id}|{payment_id}", key_secret).
     */
    public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        String payload = razorpayOrderId + "|" + razorpayPaymentId;
        return hmacMatches(payload, razorpaySignature, keySecret);
    }

    /** Verifies an inbound webhook's X-Razorpay-Signature header against the raw body. */
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        return hmacMatches(rawBody, signatureHeader, webhookSecret);
    }

    private boolean hmacMatches(String payload, String expectedSignature, String secret) {
        if (expectedSignature == null || secret == null || secret.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Razorpay HMAC verification failed: {}", e.getMessage());
            return false;
        }
    }
}
