package com.example.heail_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendOtp(String toEmail, String otp) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — Password Reset OTP");
            msg.setText("""
                    Your HEAIL password-reset OTP is:

                        %s

                    This code expires in 15 minutes.
                    If you did not request this, please ignore this email.

                    — HEAIL Platform
                    """.formatted(otp));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }
}
