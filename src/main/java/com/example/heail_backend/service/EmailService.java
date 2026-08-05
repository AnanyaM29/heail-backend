package com.example.heail_backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailKillSwitch killSwitch;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Async
    public void sendOtp(String toEmail, String otp) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendOtp");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — Password Reset OTP");
            msg.setText("""
                    Your HEAIL password-reset code is:

                        %s

                    Or just click through and it'll be filled in for you:
                    %s

                    This code expires in 15 minutes.
                    If you did not request this, please ignore this email.

                    — HEAIL Platform
                    """.formatted(otp, resetLink(toEmail, otp)));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendRegistrationOtp(String toEmail, String otp) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendRegistrationOtp");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — Verify Your Email");
            msg.setText("""
                    Your HEAIL email verification code is:

                        %s

                    Enter this code to finish creating your account.
                    This code expires in 15 minutes.

                    If you did not request this, please ignore this email.

                    — HEAIL Platform
                    """.formatted(otp));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send registration OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendAccountCreated(String toEmail, String name) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendAccountCreated");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Welcome to HEAIL — your account is ready");
            msg.setText("""
                    Dear %s,

                    Your HEAIL account has been created successfully. You can sign in
                    anytime at %s/login.

                    If you did not create this account, please contact us immediately
                    at contact@heail.in.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(name, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send account-created email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPartnerOtp(String toEmail, String otp) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendPartnerOtp");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — Verify Your Email for Partner Application");
            msg.setText("""
                    Your HEAIL partner application verification code is:

                        %s

                    Enter this code to submit your application.
                    This code expires in 15 minutes.

                    If you did not request this, please ignore this email.

                    — HEAIL Platform
                    """.formatted(otp));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send partner OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendProfileOtp(String toEmail, String otp, String reason) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendProfileOtp");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — Profile Verification Code");
            msg.setText("""
                    Your HEAIL verification code to %s is:

                        %s

                    This code expires in 15 minutes.
                    If you did not request this, please ignore this email — or contact us
                    at contact@heail.in if you're concerned about your account's security.

                    — HEAIL Platform
                    """.formatted(reason, otp));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send profile OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendProfileUpdated(String toEmail) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendProfileUpdated");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — Your Profile Was Updated");
            msg.setText("""
                    Your HEAIL account profile was just updated.

                    If this was you, no action is needed.
                    If you did not make this change, please contact us immediately
                    at contact@heail.in.

                    — HEAIL Platform
                    """);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send profile-updated email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPasswordChanged(String toEmail) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendPasswordChanged");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — Your Password Was Changed");
            msg.setText("""
                    Your HEAIL account password was just changed.

                    If this was you, no action is needed.
                    If you did not make this change, please contact us immediately
                    at contact@heail.in.

                    — HEAIL Platform
                    """);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send password-changed email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendLeaderPaymentSuccess(String toEmail, String name, String amountDisplay, String receiptRef,
                                          byte[] invoicePdf, String invoiceNumber) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendLeaderPaymentSuccess");
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true);
            helper.setTo(toEmail);
            helper.setSubject("Payment received — begin The Gita Leader");
            helper.setText("""
                    Dear %s,

                    Thank you for your payment of %s for The Gita Leader — Classic Assessment
                    (invoice attached, %s).
                    Receipt reference: %s

                    Your Classic Assessment (50 questions, about 30 minutes, one sitting) is
                    being finalised. You will be notified the moment it is ready to begin —
                    please check your email and complete the test as soon as possible after that.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(name, amountDisplay, invoiceNumber, receiptRef));
            if (invoicePdf != null) {
                helper.addAttachment(invoiceNumber + ".pdf", new ByteArrayResource(invoicePdf));
            }
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send leader payment success email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendLeaderResultsReady(String toEmail, String name) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendLeaderResultsReady");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Your results are on your dashboard");
            msg.setText("""
                    Dear %s,

                    Your Gita Leader results — overall score, band, and all five domain
                    scores — are live on your personal dashboard: %s.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(name, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send leader results-ready email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendOrgPaymentSuccess(String toEmail, String adminName, String amountDisplay, int employeeCount,
                                       String receiptRef, byte[] invoicePdf, String invoiceNumber) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendOrgPaymentSuccess");
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true);
            helper.setTo(toEmail);
            helper.setSubject("Payment received — your HEAIL Diagnostic is live");
            helper.setText("""
                    Dear %s,

                    Thank you for making the payment of %s (invoice attached, %s).
                    Receipt reference: %s

                    Assessments for %d employees have been dispatched to their email addresses.
                    Please ask your employees to check their email and complete all four
                    sections of the tests as soon as possible. Track live progress anytime
                    at %s.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(adminName, amountDisplay, invoiceNumber, receiptRef, employeeCount, frontendBaseUrl));
            if (invoicePdf != null) {
                helper.addAttachment(invoiceNumber + ".pdf", new ByteArrayResource(invoicePdf));
            }
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send org payment success email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendEmployeeInvitation(String toEmail, String employeeName, String organisationName) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendEmployeeInvitation");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject(organisationName + " has invited you — HEAIL Diagnostic");
            msg.setText("""
                    Dear %s,

                    %s has enrolled you in the HEAIL 4-Pulse Diagnostic — four short
                    assessments of about 23 minutes each. Your individual answers are
                    confidential and never shared with your organisation; results are
                    aggregate only. Please answer honestly — the outcome depends on it.

                    A separate email has just been sent to this address with a link to
                    set your password. Once that's done, sign in here: %s/login

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(employeeName, organisationName, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send employee invitation to {}: {}", toEmail, e.getMessage());
        }
    }

    /* ── Employee reminder: hasn't started any Pulse yet (24h cadence) ── */
    @Async
    public void sendEmployeeReminderNotStarted(String toEmail, String employeeName, String organisationName) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendEmployeeReminderNotStarted");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Reminder — your HEAIL Diagnostic is waiting");
            msg.setText("""
                    Dear %s,

                    This is a reminder that %s has enrolled you in the HEAIL 4-Pulse
                    Diagnostic and you have not yet started. It takes about four short
                    sittings of around 23 minutes each. Please sign in and begin as soon
                    as possible: %s/login

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(employeeName, organisationName, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send not-started reminder to {}: {}", toEmail, e.getMessage());
        }
    }

    /* ── Employee reminder: some Pulses done, some pending (12h cadence) ── */
    @Async
    public void sendEmployeeReminderPending(String toEmail, String employeeName, List<String> pendingPulseNames) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendEmployeeReminderPending");
            return;
        }
        try {
            String pendingList = pendingPulseNames.stream().map(p -> "  • " + p).reduce("", (a, b) -> a + b + "\n");
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Reminder — you still have Pulses pending");
            msg.setText("""
                    Dear %s,

                    You're partway through the HEAIL 4-Pulse Diagnostic. The following
                    Pulses are still pending:

                    %s
                    Please sign in and complete them as soon as possible: %s/login

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(employeeName, pendingList, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send pending reminder to {}: {}", toEmail, e.getMessage());
        }
    }

    /* ── Org admin: list of employees who haven't started (day 3+) ── */
    @Async
    public void sendOrgNonStarterList(String toEmail, String adminName, List<String> nonStarterNames) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendOrgNonStarterList");
            return;
        }
        try {
            String list = nonStarterNames.stream().map(n -> "  • " + n).reduce("", (a, b) -> a + b + "\n");
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL Diagnostic — employees yet to start");
            msg.setText("""
                    Dear %s,

                    A few days into your organisation's HEAIL Diagnostic round, the
                    following employees have not yet started:

                    %s
                    You may want to follow up with them directly. Track live progress
                    anytime at %s/dashboard.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(adminName, list, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send non-starter list to {}: {}", toEmail, e.getMessage());
        }
    }

    /* ── Org admin: detailed completion status (day 5+) ── */
    @Async
    public void sendOrgDetailedStatus(String toEmail, String adminName, int completed, int total) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendOrgDetailedStatus");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL Diagnostic — progress update");
            msg.setText("""
                    Dear %s,

                    Your organisation's HEAIL Diagnostic round is underway: %d of %d
                    employees have fully completed all four Pulses so far. The report
                    is released once everyone has finished, or automatically on day 8
                    if at least 80%% have completed. Track live progress anytime at
                    %s/dashboard.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(adminName, completed, total, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send detailed status to {}: {}", toEmail, e.getMessage());
        }
    }

    /* ── Org admin: day-8 final notice (whether or not the report released) ── */
    @Async
    public void sendOrgDay8Final(String toEmail, String adminName, int completed, int total) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendOrgDay8Final");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL Diagnostic — day 8 status");
            msg.setText("""
                    Dear %s,

                    It's been 8 days since your organisation's HEAIL Diagnostic round
                    began: %d of %d employees have fully completed all four Pulses.
                    Check your dashboard for the latest status and, if the completion
                    threshold has been met, your report: %s/dashboard.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(adminName, completed, total, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send day-8 final notice to {}: {}", toEmail, e.getMessage());
        }
    }

    /* ── Org admin: report is ready ── */
    @Async
    public void sendOrgReportReleased(String toEmail, String adminName) {
        if (!killSwitch.isEnabled()) {
            log.info("Email sending disabled — skipped sendOrgReportReleased");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Your HEAIL Diagnostic report is ready");
            msg.setText("""
                    Dear %s,

                    Your organisation's HEAIL Diagnostic report — RAG status across all
                    20 sections and each of the four Pulses — is now live on your
                    dashboard: %s/dashboard.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(adminName, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send report-released email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String resetLink(String email, String otp) {
        try {
            String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8.name());
            return frontendBaseUrl + "/reset-password?email=" + encodedEmail + "&otp=" + otp;
        } catch (UnsupportedEncodingException e) {
            return frontendBaseUrl + "/reset-password";
        }
    }
}
