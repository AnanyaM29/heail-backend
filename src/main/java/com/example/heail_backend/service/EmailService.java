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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Async
    public void sendOtp(String toEmail, String otp) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendOtp — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendRegistrationOtp — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendAccountCreated — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendPartnerOtp — no recipient email");
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
    public void sendContactOtp(String toEmail, String otp) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendContactOtp — no recipient email");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — Verify Your Details for Get in Touch");
            msg.setText("""
                    Your HEAIL "Get in Touch" verification code is:

                        %s

                    Enter this code to send your message to us.
                    This code expires in 15 minutes.

                    If you did not request this, please ignore this email.

                    — HEAIL Platform
                    """.formatted(otp));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send contact OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Delivers a "Get in Touch" submission to HEAIL's own inbox, not the sender. */
    @Async
    public void sendContactMessageToHeail(String heailEmail, String name, String mobile, String email,
                                           String city, String country, String message) {
        if (heailEmail == null || heailEmail.isBlank()) {
            log.info("Skipped sendContactMessageToHeail — no recipient configured");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(heailEmail);
            msg.setReplyTo(email);
            msg.setSubject("HEAIL — New Get in Touch message from " + name);
            msg.setText("""
                    New "Get in Touch" submission:

                    Name: %s
                    Mobile: %s
                    Email: %s
                    City: %s
                    Country: %s

                    Message:
                    %s
                    """.formatted(name, mobile, email, city, country, message));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send contact message notification: {}", e.getMessage());
        }
    }

    @Async
    public void sendProfileOtp(String toEmail, String otp, String reason) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendProfileOtp — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendProfileUpdated — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendPasswordChanged — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendLeaderPaymentSuccess — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendLeaderResultsReady — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendOrgPaymentSuccess — no recipient email");
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

    /** Sent to the recipient a superadmin targets a coupon at, at generation time. */
    @Async
    public void sendCouponCode(String toEmail, String code, int discountPercent, LocalDateTime expiresAt) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendCouponCode — no recipient email");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Your HEAIL discount code");
            msg.setText("""
                    Hi,

                    You've been sent a %d%% discount code for HEAIL: %s

                    Enter it on the "Before you pay" step of checkout. It's valid only for
                    this email address, good for one use, and expires on %s.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(discountPercent, code,
                    expiresAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a"))));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send coupon code email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** 100%-discount coupon redemption — no payment, no invoice, access already granted. */
    @Async
    public void sendLeaderFreeAccessGranted(String toEmail, String name) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendLeaderFreeAccessGranted — no recipient email");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Your access is ready — begin The Gita Leader");
            msg.setText("""
                    Dear %s,

                    A 100%% discount coupon was applied to your order — no payment was
                    required, and no invoice was generated.

                    Your Classic Assessment (50 questions, about 30 minutes, one sitting) is
                    being finalised. You will be notified the moment it is ready to begin —
                    please check your email and complete the test as soon as possible after that.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(name));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send leader free-access email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** 100%-discount coupon redemption — no payment, no invoice, access already granted. */
    @Async
    public void sendOrgFreeAccessGranted(String toEmail, String adminName, int employeeCount) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendOrgFreeAccessGranted — no recipient email");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("Your HEAIL Diagnostic is live — no charge");
            msg.setText("""
                    Dear %s,

                    A 100%% discount coupon was applied to your order — no payment was
                    required, and no invoice was generated.

                    Assessments for %d employees have been dispatched to their email addresses.
                    Please ask your employees to check their email and complete all four
                    sections of the tests as soon as possible. Track live progress anytime
                    at %s.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(adminName, employeeCount, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send org free-access email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** 100%-discount coupon redemption — no payment, no invoice, access already granted. */
    @Async
    public void sendHrFreeAccessGranted(String toEmail, String buyerName, int candidateCount) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendHrFreeAccessGranted — no recipient email");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HR assessment invites sent — no charge");
            msg.setText("""
                    Dear %s,

                    A 100%% discount coupon was applied to your order — no payment was
                    required, and no invoice was generated.

                    Invitations for %d candidate(s) have been dispatched to their email
                    addresses.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(buyerName, candidateCount));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send HR free-access email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendEmployeeInvitation(String toEmail, String employeeName, String organisationName) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendEmployeeInvitation — no recipient email");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject(organisationName + " has invited you — HEAIL Diagnostic");
            msg.setText("""
                    Dear %s,

                    %s has enrolled you in the HEAIL 4-Pulse Diagnostic — four short
                    assessments of about 30 minutes each. Your individual answers are
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendEmployeeReminderNotStarted — no recipient email");
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
                    sittings of around 30 minutes each. Please sign in and begin as soon
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendEmployeeReminderPending — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendOrgNonStarterList — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendOrgDetailedStatus — no recipient email");
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
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendOrgDay8Final — no recipient email");
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

    /* ── Org admin: report is ready — full PDF attached, not just a pointer to the dashboard ── */
    @Async
    public void sendOrgReportReleased(String toEmail, String adminName, byte[] reportPdf) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendOrgReportReleased — no recipient email");
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true);
            helper.setTo(toEmail);
            helper.setSubject("Your HEAIL Diagnostic report is ready");
            helper.setText("""
                    Dear %s,

                    Your organisation's HEAIL Diagnostic report — RAG status across all
                    20 sections and each of the four Pulses — is ready, attached as a PDF,
                    and also live on your dashboard: %s/dashboard.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(adminName, frontendBaseUrl));
            if (reportPdf != null) {
                helper.addAttachment("HEAIL-Diagnostic-Report.pdf", new ByteArrayResource(reportPdf));
            }
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send report-released email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPaymentReminder(String toEmail, String name, String productLabel, String amountDisplay) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendPaymentReminder — no recipient email");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — Your payment is still pending");
            msg.setText("""
                    Dear %s,

                    We noticed your payment for %s (%s) hasn't been completed yet.
                    Please sign in to complete your payment: %s/login

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(name, productLabel, amountDisplay, frontendBaseUrl));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send payment reminder email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendInvoice(String toEmail, String name, String amountDisplay, byte[] invoicePdf, String invoiceNumber) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendInvoice — no recipient email");
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true);
            helper.setTo(toEmail);
            helper.setSubject("HEAIL — Your invoice " + invoiceNumber);
            helper.setText("""
                    Dear %s,

                    As requested, please find attached your invoice %s for %s.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(name, invoiceNumber, amountDisplay));
            if (invoicePdf != null) {
                helper.addAttachment(invoiceNumber + ".pdf", new ByteArrayResource(invoicePdf));
            }
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send invoice email to {}: {}", toEmail, e.getMessage());
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

    /* ── HR candidate flow — a buyer registers candidates, each gets a
       tokenized link (no HEAIL account/password) to take their assigned
       assessments. See HrOrderService.fulfilCandidates(). ──────────────── */

    private String candidateLink(String accessToken) {
        return frontendBaseUrl + "/hr/candidate/" + accessToken;
    }

    /** @param buyerEmailCc CC'd on the candidate's invitation so the HR buyer
     *  who registered them has visibility that it actually went out — the
     *  buyer never gets their own copy of the assessment link otherwise,
     *  since entitlements belong to the candidate, not them. */
    @Async
    public void sendCandidateInvitation(String toEmail, String candidateName, List<String> assessmentNames,
                                         String accessToken, LocalDateTime expiresAt, String buyerEmailCc) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendCandidateInvitation — no recipient email");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            if (buyerEmailCc != null && !buyerEmailCc.isBlank()) msg.setCc(buyerEmailCc);
            msg.setSubject("You've been invited to take an assessment — HEAIL");
            msg.setText("""
                    Dear %s,

                    You've been invited to complete the following HEAIL assessment(s):

                    %s

                    Each is 30 questions, timed at 30 minutes, completed in a single
                    sitting — closing the browser or losing connection marks the
                    attempt abandoned, so make sure you have an uninterrupted block of
                    time before you begin.

                    Start here (no account or password needed):
                    %s

                    This link expires on %s and cannot be renewed — if it lapses before
                    you start, contact the person who invited you.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(candidateName, String.join("\n", assessmentNames.stream().map(n -> "  • " + n).toList()),
                    candidateLink(accessToken), expiresAt.toLocalDate()));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send candidate invitation to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendBuyerCandidateExpired(String toEmail, String buyerName, String candidateName) {
        if (toEmail == null || toEmail.isBlank()) {
            log.info("Skipped sendBuyerCandidateExpired — no recipient email");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("HEAIL — A candidate's assessment window has expired");
            msg.setText("""
                    Dear %s,

                    %s did not start their assessment within the 7-day access window,
                    and the link has now expired permanently. To assign this
                    assessment to someone else, purchase a new test license.

                    — Team HEAIL
                    contact@heail.in
                    """.formatted(buyerName, candidateName));
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send candidate-expired email to {}: {}", toEmail, e.getMessage());
        }
    }
}
