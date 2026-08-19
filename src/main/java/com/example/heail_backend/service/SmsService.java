package com.example.heail_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Talks to MSG91's OTP API directly (same "no SDK" approach as RazorpayService)
 * to text out the same OTP already generated for email, so one code works via
 * either channel.
 */
@Slf4j
@Service
public class SmsService {

    @Value("${msg91.auth-key}")
    private String authKey;

    @Value("${msg91.otp-template-id}")
    private String templateId;

    @Value("${msg91.api-base}")
    private String apiBase;

    @Value("${app.sms.msg91-enabled:false}")
    private boolean enabled;

    private RestClient client() {
        return RestClient.builder().baseUrl(apiBase).build();
    }

    /** Sends the given OTP by SMS. No-op if there's no mobile to send to, or if disabled. */
    @Async
    public void sendOtp(String mobile, String otp) {
        if (mobile == null || mobile.isBlank()) {
            log.info("Skipped SMS OTP — no recipient mobile number");
            return;
        }
        if (!enabled) {
            log.info("SMS sending disabled — skipped OTP SMS to {}", mobile);
            return;
        }
        try {
            client().get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v5/otp")
                            .queryParam("template_id", templateId)
                            .queryParam("mobile", normalize(mobile))
                            .queryParam("authkey", authKey)
                            .queryParam("otp", otp)
                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send OTP SMS to {}: {}", mobile, e.getMessage());
        }
    }

    /** MSG91 expects a bare country-code+number, digits only — strip any '+', spaces, etc. */
    private String normalize(String mobile) {
        return mobile.replaceAll("\\D", "");
    }
}
