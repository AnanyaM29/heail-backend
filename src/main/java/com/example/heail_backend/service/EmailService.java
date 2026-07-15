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

    @Async
    public void sendLeaderPaymentSuccess(String toEmail, String name, String amountDisplay, String receiptRef) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Payment received — begin The Gita Leader");
            msg.setText("""
                    Dear %s,

                    Thank you for your payment of %s for The Gita Leader — Classic Assessment.
                    Receipt reference: %s

                    Your Classic Assessment (50 questions, about 30 minutes, one sitting) is
                    being finalised. You will be notified the moment it is ready to begin —
                    please check your email and complete the test as soon as possible after that.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(name, amountDisplay, receiptRef));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send leader payment success email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendLeaderResultsReady(String toEmail, String name) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Your results are on your dashboard");
            msg.setText("""
                    Dear %s,

                    Your Gita Leader results — overall score, band, and all five domain
                    scores — are live on your personal dashboard: heail.in.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(name));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send leader results-ready email to {}: {}", toEmail, e.getMessage());
        }
    }
}
