package com.example.heail_backend.service;

import com.example.heail_backend.email.EmailTemplateType;
import com.example.heail_backend.entity.EmailTemplate;
import com.example.heail_backend.repository.EmailTemplateRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends every transactional email. Wording per type lives in {@link EmailTemplateType}
 * as a built-in default; a row in {@code email_template} (edited from the admin console)
 * overrides it. Each method here only assembles the placeholder values — subject/body
 * text is resolved and rendered centrally by {@link #render}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailTemplateRepository templateRepo;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private record Rendered(String subject, String body) {}

    private Rendered render(EmailTemplateType type, Map<String, String> vars) {
        EmailTemplate override = templateRepo.findById(type.getKey()).orElse(null);
        String subject = override != null ? override.getSubject() : type.getDefaultSubject();
        String body = override != null ? override.getBody() : type.getDefaultBody();
        Map<String, String> all = new HashMap<>(vars);
        all.putIfAbsent("frontendBaseUrl", frontendBaseUrl);
        return new Rendered(substitute(subject, all), substitute(body, all));
    }

    private static String substitute(String text, Map<String, String> vars) {
        String out = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** Plain-text send, no attachment/cc. Swallows failures (logged) like all the callers expect. */
    private void dispatch(EmailTemplateType type, String to, Map<String, String> vars) {
        send(type, to, null, null, vars, null, null);
    }

    private void send(EmailTemplateType type, String to, String cc, String replyTo,
                      Map<String, String> vars, String attachmentName, byte[] attachment) {
        if (to == null || to.isBlank()) {
            log.info("Skipped {} — no recipient email", type.getKey());
            return;
        }
        try {
            Rendered r = render(type, vars);
            if (attachment == null && cc == null && replyTo == null) {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(to);
                msg.setSubject(r.subject());
                msg.setText(r.body());
                mailSender.send(msg);
            } else {
                MimeMessage mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, attachment != null);
                helper.setTo(to);
                if (cc != null && !cc.isBlank()) helper.setCc(cc);
                if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
                helper.setSubject(r.subject());
                helper.setText(r.body());
                if (attachment != null) helper.addAttachment(attachmentName, new ByteArrayResource(attachment));
                mailSender.send(mime);
            }
        } catch (Exception e) {
            log.error("Failed to send {} to {}: {}", type.getKey(), to, e.getMessage());
        }
    }

    /* ── Admin "send test" — synchronous, sample values, lets failures surface ── */

    private static final Map<String, String> SAMPLE_VARS = Map.ofEntries(
            Map.entry("name", "Jane Doe"),
            Map.entry("adminName", "Jane Doe"),
            Map.entry("buyerName", "Jane Doe"),
            Map.entry("employeeName", "Jane Doe"),
            Map.entry("candidateName", "Jane Doe"),
            Map.entry("otp", "123456"),
            Map.entry("resetLink", "https://heail.in/reset-password?email=jane%40acme.com&otp=123456"),
            Map.entry("amount", "INR 2,499.00"),
            Map.entry("invoiceNumber", "HEAIL-INV-000042"),
            Map.entry("receiptRef", "pay_ABC123XYZ"),
            Map.entry("employeeCount", "12"),
            Map.entry("candidateCount", "3"),
            Map.entry("discountPercent", "100"),
            Map.entry("code", "HEAILX7Q2"),
            Map.entry("expiresAt", "31 Mar 2027"),
            Map.entry("reason", "change your email"),
            Map.entry("organisationName", "Acme Corp"),
            Map.entry("productLabel", "The Gita Leader — Classic Assessment"),
            Map.entry("mobile", "+91 98765 43210"),
            Map.entry("email", "jane@acme.com"),
            Map.entry("city", "Jaipur"),
            Map.entry("country", "India"),
            Map.entry("message", "Hello, I'd like to know more."),
            Map.entry("completed", "8"),
            Map.entry("total", "12"),
            Map.entry("loginBlock", "Your HEAIL sign-in details:\n    Email:    jane@acme.com\n    Password: Xy7kPq3mRt\nYou can change this password once you're signed in."),
            Map.entry("pendingList", "  • Talent Pulse\n  • Growth Pulse\n"),
            Map.entry("list", "  • John Smith\n  • Priya Patel\n"),
            Map.entry("assessmentList", "  • HR Competency Assessment\n"),
            Map.entry("candidateLink", "https://heail.in/hr/candidate/sample-token")
    );

    public void sendTest(EmailTemplateType type, String toEmail) {
        Rendered r = render(type, new HashMap<>(SAMPLE_VARS));
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(toEmail);
        msg.setSubject("[TEST] " + r.subject());
        msg.setText(r.body());
        mailSender.send(msg);
    }

    /* ── OTP / verification ─────────────────────────────────────── */

    @Async
    public void sendOtp(String toEmail, String otp) {
        dispatch(EmailTemplateType.PASSWORD_RESET_OTP, toEmail,
                Map.of("otp", otp, "resetLink", resetLink(toEmail, otp)));
    }

    @Async
    public void sendRegistrationOtp(String toEmail, String otp) {
        dispatch(EmailTemplateType.REGISTRATION_OTP, toEmail, Map.of("otp", otp));
    }

    @Async
    public void sendAccountCreated(String toEmail, String name) {
        dispatch(EmailTemplateType.ACCOUNT_CREATED, toEmail, Map.of("name", name));
    }

    @Async
    public void sendPartnerOtp(String toEmail, String otp) {
        dispatch(EmailTemplateType.PARTNER_OTP, toEmail, Map.of("otp", otp));
    }

    @Async
    public void sendContactOtp(String toEmail, String otp) {
        dispatch(EmailTemplateType.CONTACT_OTP, toEmail, Map.of("otp", otp));
    }

    /** Delivers a "Get in Touch" submission to HEAIL's own inbox, not the sender. */
    @Async
    public void sendContactMessageToHeail(String heailEmail, String name, String mobile, String email,
                                          String city, String country, String message) {
        send(EmailTemplateType.CONTACT_MESSAGE_TO_HEAIL, heailEmail, null, email,
                Map.of("name", name, "mobile", mobile, "email", email,
                        "city", city, "country", country, "message", message),
                null, null);
    }

    @Async
    public void sendProfileOtp(String toEmail, String otp, String reason) {
        dispatch(EmailTemplateType.PROFILE_OTP, toEmail, Map.of("reason", reason, "otp", otp));
    }

    @Async
    public void sendProfileUpdated(String toEmail) {
        dispatch(EmailTemplateType.PROFILE_UPDATED, toEmail, Map.of());
    }

    @Async
    public void sendPasswordChanged(String toEmail) {
        dispatch(EmailTemplateType.PASSWORD_CHANGED, toEmail, Map.of());
    }

    /* ── Payments / access ─────────────────────────────────────── */

    @Async
    public void sendLeaderPaymentSuccess(String toEmail, String name, String amountDisplay, String receiptRef,
                                         byte[] invoicePdf, String invoiceNumber) {
        send(EmailTemplateType.LEADER_PAYMENT_SUCCESS, toEmail, null, null,
                Map.of("name", name, "amount", amountDisplay, "invoiceNumber", invoiceNumber, "receiptRef", receiptRef),
                invoiceNumber + ".pdf", invoicePdf);
    }

    @Async
    public void sendLeaderResultsReady(String toEmail, String name) {
        dispatch(EmailTemplateType.LEADER_RESULTS_READY, toEmail, Map.of("name", name));
    }

    @Async
    public void sendOrgPaymentSuccess(String toEmail, String adminName, String amountDisplay, int employeeCount,
                                      String receiptRef, byte[] invoicePdf, String invoiceNumber) {
        send(EmailTemplateType.ORG_PAYMENT_SUCCESS, toEmail, null, null,
                Map.of("adminName", adminName, "amount", amountDisplay, "invoiceNumber", invoiceNumber,
                        "receiptRef", receiptRef, "employeeCount", String.valueOf(employeeCount)),
                invoiceNumber + ".pdf", invoicePdf);
    }

    /** Sent to the recipient a superadmin targets a coupon at, at generation time. */
    @Async
    public void sendCouponCode(String toEmail, String code, int discountPercent, LocalDateTime expiresAt) {
        dispatch(EmailTemplateType.COUPON_CODE, toEmail, Map.of(
                "discountPercent", String.valueOf(discountPercent),
                "code", code,
                "expiresAt", expiresAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a"))));
    }

    /** 100%-discount coupon redemption — no payment, no invoice, access already granted. */
    @Async
    public void sendLeaderFreeAccessGranted(String toEmail, String name) {
        dispatch(EmailTemplateType.LEADER_FREE_ACCESS, toEmail, Map.of("name", name));
    }

    /** 100%-discount coupon redemption — no payment, no invoice, access already granted. */
    @Async
    public void sendOrgFreeAccessGranted(String toEmail, String adminName, int employeeCount) {
        dispatch(EmailTemplateType.ORG_FREE_ACCESS, toEmail,
                Map.of("adminName", adminName, "employeeCount", String.valueOf(employeeCount)));
    }

    /** 100%-discount coupon redemption — no payment, no invoice, access already granted. */
    @Async
    public void sendHrFreeAccessGranted(String toEmail, String buyerName, int candidateCount) {
        dispatch(EmailTemplateType.HR_FREE_ACCESS, toEmail,
                Map.of("buyerName", buyerName, "candidateCount", String.valueOf(candidateCount)));
    }

    /* ── Pulse round: employee + org-admin lifecycle ───────────── */

    @Async
    /** @param issuedPassword the generated password for a brand-new respondent
     *  account, or null when the account already existed. Included in the body
     *  either way — this email goes only to the respondent, never CC'd. */
    public void sendEmployeeInvitation(String toEmail, String employeeName, String organisationName,
                                       String issuedPassword) {
        String loginBlock = issuedPassword != null
                ? "Your HEAIL sign-in details:\n"
                  + "    Email:    " + toEmail + "\n"
                  + "    Password: " + issuedPassword + "\n"
                  + "You can change this password once you're signed in."
                : "Sign in with your existing HEAIL account for this email address — "
                  + "use \"Forgot password\" on the sign-in page if you need to reset it.";
        dispatch(EmailTemplateType.EMPLOYEE_INVITATION, toEmail, Map.of(
                "employeeName", employeeName,
                "organisationName", organisationName,
                "loginBlock", loginBlock));
    }

    @Async
    public void sendEmployeeReminderNotStarted(String toEmail, String employeeName, String organisationName) {
        dispatch(EmailTemplateType.EMPLOYEE_REMINDER_NOT_STARTED, toEmail,
                Map.of("employeeName", employeeName, "organisationName", organisationName));
    }

    @Async
    public void sendEmployeeReminderPending(String toEmail, String employeeName, List<String> pendingPulseNames) {
        String pendingList = pendingPulseNames.stream().map(p -> "  • " + p).reduce("", (a, b) -> a + b + "\n");
        dispatch(EmailTemplateType.EMPLOYEE_REMINDER_PENDING, toEmail,
                Map.of("employeeName", employeeName, "pendingList", pendingList));
    }

    @Async
    public void sendOrgNonStarterList(String toEmail, String adminName, List<String> nonStarterNames) {
        String list = nonStarterNames.stream().map(n -> "  • " + n).reduce("", (a, b) -> a + b + "\n");
        dispatch(EmailTemplateType.ORG_NON_STARTER_LIST, toEmail,
                Map.of("adminName", adminName, "list", list));
    }

    @Async
    public void sendOrgDetailedStatus(String toEmail, String adminName, int completed, int total) {
        dispatch(EmailTemplateType.ORG_DETAILED_STATUS, toEmail,
                Map.of("adminName", adminName, "completed", String.valueOf(completed), "total", String.valueOf(total)));
    }

    @Async
    public void sendOrgDay8Final(String toEmail, String adminName, int completed, int total) {
        dispatch(EmailTemplateType.ORG_DAY8_FINAL, toEmail,
                Map.of("adminName", adminName, "completed", String.valueOf(completed), "total", String.valueOf(total)));
    }

    @Async
    public void sendOrgReportReleased(String toEmail, String adminName, byte[] reportPdf) {
        send(EmailTemplateType.ORG_REPORT_RELEASED, toEmail, null, null,
                Map.of("adminName", adminName), "HEAIL-Diagnostic-Report.pdf", reportPdf);
    }

    /* ── Invoicing ─────────────────────────────────────────────── */

    @Async
    public void sendPaymentReminder(String toEmail, String name, String productLabel, String amountDisplay) {
        dispatch(EmailTemplateType.PAYMENT_REMINDER, toEmail,
                Map.of("name", name, "productLabel", productLabel, "amount", amountDisplay));
    }

    @Async
    public void sendInvoice(String toEmail, String name, String amountDisplay, byte[] invoicePdf, String invoiceNumber) {
        send(EmailTemplateType.INVOICE, toEmail, null, null,
                Map.of("name", name, "invoiceNumber", invoiceNumber, "amount", amountDisplay),
                invoiceNumber + ".pdf", invoicePdf);
    }

    /* ── HR candidate flow — tokenized link, no HEAIL account ──── */

    /** Goes only to the candidate who takes the assessment — the buyer who
     *  registered them is not CC'd. */
    @Async
    public void sendCandidateInvitation(String toEmail, String candidateName, List<String> assessmentNames,
                                        String accessToken, LocalDateTime expiresAt) {
        String assessmentList = String.join("\n", assessmentNames.stream().map(n -> "  • " + n).toList());
        send(EmailTemplateType.CANDIDATE_INVITATION, toEmail, null, null,
                Map.of("candidateName", candidateName, "assessmentList", assessmentList,
                        "candidateLink", candidateLink(accessToken),
                        "expiresAt", expiresAt.toLocalDate().toString()),
                null, null);
    }

    @Async
    public void sendBuyerCandidateExpired(String toEmail, String buyerName, String candidateName) {
        dispatch(EmailTemplateType.BUYER_CANDIDATE_EXPIRED, toEmail,
                Map.of("buyerName", buyerName, "candidateName", candidateName));
    }

    /* ── Link builders ─────────────────────────────────────────── */

    private String resetLink(String email, String otp) {
        try {
            String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8.name());
            return frontendBaseUrl + "/reset-password?email=" + encodedEmail + "&otp=" + otp;
        } catch (UnsupportedEncodingException e) {
            return frontendBaseUrl + "/reset-password";
        }
    }

    private String candidateLink(String accessToken) {
        return frontendBaseUrl + "/hr/candidate/" + accessToken;
    }
}
