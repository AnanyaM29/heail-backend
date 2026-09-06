package com.example.heail_backend.email;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every transactional email HEAIL sends, with its built-in default subject and body.
 * The body uses {@code {placeholder}} tokens (not {@code %s}) so a superadmin can edit
 * the wording from the admin console without tracking argument positions.
 *
 * A row in {@code email_template} keyed by {@link #getKey()} overrides the default;
 * absent that, the defaults here are used, so an empty table changes nothing.
 * {@code {frontendBaseUrl}} is always available and injected automatically.
 */
public enum EmailTemplateType {

    PASSWORD_RESET_OTP("password_reset_otp", "Password reset OTP",
            "HEAIL — Password Reset OTP",
            """
            Your HEAIL password-reset code is:

                {otp}

            Or just click through and it'll be filled in for you:
            {resetLink}

            This code expires in 15 minutes.
            If you did not request this, please ignore this email.

            — HEAIL Platform
            """,
            List.of("otp", "resetLink")),

    REGISTRATION_OTP("registration_otp", "Registration — verify email",
            "HEAIL — Verify Your Email",
            """
            Your HEAIL email verification code is:

                {otp}

            Enter this code to finish creating your account.
            This code expires in 15 minutes.

            If you did not request this, please ignore this email.

            — HEAIL Platform
            """,
            List.of("otp")),

    ACCOUNT_CREATED("account_created", "Account created / welcome",
            "Welcome to HEAIL — your account is ready",
            """
            Dear {name},

            Your HEAIL account has been created successfully. You can sign in
            anytime at {frontendBaseUrl}/login.

            If you did not create this account, please contact us immediately
            at contact@heail.in.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("name", "frontendBaseUrl")),

    PARTNER_OTP("partner_otp", "Partner application OTP",
            "HEAIL — Verify Your Email for Partner Application",
            """
            Your HEAIL partner application verification code is:

                {otp}

            Enter this code to submit your application.
            This code expires in 15 minutes.

            If you did not request this, please ignore this email.

            — HEAIL Platform
            """,
            List.of("otp")),

    CONTACT_OTP("contact_otp", "\"Get in Touch\" OTP",
            "HEAIL — Verify Your Details for Get in Touch",
            """
            Your HEAIL "Get in Touch" verification code is:

                {otp}

            Enter this code to send your message to us.
            This code expires in 15 minutes.

            If you did not request this, please ignore this email.

            — HEAIL Platform
            """,
            List.of("otp")),

    CONTACT_MESSAGE_TO_HEAIL("contact_message_to_heail", "\"Get in Touch\" submission (to HEAIL inbox)",
            "HEAIL — New Get in Touch message from {name}",
            """
            New "Get in Touch" submission:

            Name: {name}
            Mobile: {mobile}
            Email: {email}
            City: {city}
            Country: {country}

            Message:
            {message}
            """,
            List.of("name", "mobile", "email", "city", "country", "message")),

    PROFILE_OTP("profile_otp", "Profile change verification OTP",
            "HEAIL — Profile Verification Code",
            """
            Your HEAIL verification code to {reason} is:

                {otp}

            This code expires in 15 minutes.
            If you did not request this, please ignore this email — or contact us
            at contact@heail.in if you're concerned about your account's security.

            — HEAIL Platform
            """,
            List.of("reason", "otp")),

    PROFILE_UPDATED("profile_updated", "Profile updated notification",
            "HEAIL — Your Profile Was Updated",
            """
            Your HEAIL account profile was just updated.

            If this was you, no action is needed.
            If you did not make this change, please contact us immediately
            at contact@heail.in.

            — HEAIL Platform
            """,
            List.of()),

    PASSWORD_CHANGED("password_changed", "Password changed notification",
            "HEAIL — Your Password Was Changed",
            """
            Your HEAIL account password was just changed.

            If this was you, no action is needed.
            If you did not make this change, please contact us immediately
            at contact@heail.in.

            — HEAIL Platform
            """,
            List.of()),

    LEADER_PAYMENT_SUCCESS("leader_payment_success", "Leader — payment received",
            "Payment received — begin The Gita Leader",
            """
            Dear {name},

            Thank you for your payment of {amount} for The Gita Leader — Classic Assessment
            (invoice attached, {invoiceNumber}).
            Receipt reference: {receiptRef}

            Your Classic Assessment (50 questions, about 30 minutes, one sitting) is
            being finalised. You will be notified the moment it is ready to begin.

            When it's ready, sign in here to start:
            {frontendBaseUrl}/take-test/leader

            — Team HEAIL
            contact@heail.in
            """,
            List.of("name", "amount", "invoiceNumber", "receiptRef", "frontendBaseUrl")),

    LEADER_RESULTS_READY("leader_results_ready", "Leader — results ready",
            "Your results are on your dashboard",
            """
            Dear {name},

            Your Gita Leader results — overall score, band, and all five domain
            scores — are live on your personal dashboard: {frontendBaseUrl}.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("name", "frontendBaseUrl")),

    ORG_PAYMENT_SUCCESS("org_payment_success", "Org — payment received",
            "Payment received — your HEAIL Diagnostic is live",
            """
            Dear {adminName},

            Thank you for making the payment of {amount} (invoice attached, {invoiceNumber}).
            Receipt reference: {receiptRef}

            Assessments for {employeeCount} employees have been dispatched to their email addresses.
            Please ask your employees to check their email and complete all four
            sections of the tests as soon as possible. Track live progress anytime
            at {frontendBaseUrl}.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("adminName", "amount", "invoiceNumber", "receiptRef", "employeeCount", "frontendBaseUrl")),

    COUPON_CODE("coupon_code", "Discount coupon code",
            "Your HEAIL discount code",
            """
            Hi,

            You've been sent a {discountPercent}% discount code for HEAIL: {code}

            Enter it on the "Before you pay" step of checkout. It's valid only for
            this email address, good for one use, and expires on {expiresAt}.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("discountPercent", "code", "expiresAt")),

    LEADER_FREE_ACCESS("leader_free_access", "Leader — free access (100% coupon)",
            "Your access is ready — begin The Gita Leader",
            """
            Dear {name},

            A 100% discount coupon was applied to your order — no payment was
            required, and no invoice was generated.

            Your Classic Assessment (50 questions, about 30 minutes, one sitting) is
            being finalised. You will be notified the moment it is ready to begin.

            When it's ready, sign in here to start:
            {frontendBaseUrl}/take-test/leader

            — Team HEAIL
            contact@heail.in
            """,
            List.of("name", "frontendBaseUrl")),

    ORG_FREE_ACCESS("org_free_access", "Org — free access (100% coupon)",
            "Your HEAIL Diagnostic is live — no charge",
            """
            Dear {adminName},

            A 100% discount coupon was applied to your order — no payment was
            required, and no invoice was generated.

            Assessments for {employeeCount} employees have been dispatched to their email addresses.
            Please ask your employees to check their email and complete all four
            sections of the tests as soon as possible. Track live progress anytime
            at {frontendBaseUrl}.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("adminName", "employeeCount", "frontendBaseUrl")),

    HR_FREE_ACCESS("hr_free_access", "HR — free access (100% coupon)",
            "HR assessment invites sent — no charge",
            """
            Dear {buyerName},

            A 100% discount coupon was applied to your order — no payment was
            required, and no invoice was generated.

            Invitations for {candidateCount} candidate(s) have been dispatched to their email
            addresses.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("buyerName", "candidateCount")),

    EMPLOYEE_INVITATION("employee_invitation", "Employee — Pulse invitation",
            "{organisationName} has invited you — HEAIL Diagnostic",
            """
            Dear {employeeName},

            {organisationName} has enrolled you in the HEAIL 4-Pulse Diagnostic — four short
            assessments of about 30 minutes each. Your individual answers are
            confidential and never shared with your organisation; results are
            aggregate only. Please answer honestly — the outcome depends on it.

            {loginBlock}

            Sign in and begin here:
            {frontendBaseUrl}/take-test/pulse

            — Team HEAIL
            contact@heail.in
            """,
            List.of("employeeName", "organisationName", "loginBlock", "frontendBaseUrl")),

    EMPLOYEE_REMINDER_NOT_STARTED("employee_reminder_not_started", "Employee reminder — not started",
            "Reminder — your HEAIL Diagnostic is waiting",
            """
            Dear {employeeName},

            This is a reminder that {organisationName} has enrolled you in the HEAIL 4-Pulse
            Diagnostic and you have not yet started. It takes about four short
            sittings of around 30 minutes each. Please sign in and begin as soon
            as possible: {frontendBaseUrl}/take-test/pulse

            — Team HEAIL
            contact@heail.in
            """,
            List.of("employeeName", "organisationName", "frontendBaseUrl")),

    EMPLOYEE_REMINDER_PENDING("employee_reminder_pending", "Employee reminder — pulses pending",
            "Reminder — you still have Pulses pending",
            """
            Dear {employeeName},

            You're partway through the HEAIL 4-Pulse Diagnostic. The following
            Pulses are still pending:

            {pendingList}
            Please sign in and complete them as soon as possible: {frontendBaseUrl}/take-test/pulse

            — Team HEAIL
            contact@heail.in
            """,
            List.of("employeeName", "pendingList", "frontendBaseUrl")),

    ORG_NON_STARTER_LIST("org_non_starter_list", "Org — employees yet to start",
            "HEAIL Diagnostic — employees yet to start",
            """
            Dear {adminName},

            A few days into your organisation's HEAIL Diagnostic round, the
            following employees have not yet started:

            {list}
            You may want to follow up with them directly. Track live progress
            anytime at {frontendBaseUrl}/dashboard.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("adminName", "list", "frontendBaseUrl")),

    ORG_DETAILED_STATUS("org_detailed_status", "Org — progress update",
            "HEAIL Diagnostic — progress update",
            """
            Dear {adminName},

            Your organisation's HEAIL Diagnostic round is underway: {completed} of {total}
            employees have fully completed all four Pulses so far. The report
            is released once everyone has finished, or automatically on day 8
            if at least 80% have completed. Track live progress anytime at
            {frontendBaseUrl}/dashboard.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("adminName", "completed", "total", "frontendBaseUrl")),

    ORG_DAY8_FINAL("org_day8_final", "Org — day 8 status",
            "HEAIL Diagnostic — day 8 status",
            """
            Dear {adminName},

            It's been 8 days since your organisation's HEAIL Diagnostic round
            began: {completed} of {total} employees have fully completed all four Pulses.
            Check your dashboard for the latest status and, if the completion
            threshold has been met, your report: {frontendBaseUrl}/dashboard.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("adminName", "completed", "total", "frontendBaseUrl")),

    ORG_REPORT_RELEASED("org_report_released", "Org — report ready",
            "Your HEAIL Diagnostic report is ready",
            """
            Dear {adminName},

            Your organisation's HEAIL Diagnostic report — RAG status across all
            20 sections and each of the four Pulses — is ready, attached as a PDF,
            and also live on your dashboard: {frontendBaseUrl}/dashboard.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("adminName", "frontendBaseUrl")),

    PAYMENT_REMINDER("payment_reminder", "Payment pending reminder",
            "HEAIL — Your payment is still pending",
            """
            Dear {name},

            We noticed your payment for {productLabel} ({amount}) hasn't been completed yet.
            Please sign in to complete your payment: {frontendBaseUrl}/login

            — Team HEAIL
            contact@heail.in
            """,
            List.of("name", "productLabel", "amount", "frontendBaseUrl")),

    INVOICE("invoice", "Invoice (on request / resend)",
            "HEAIL — Your invoice {invoiceNumber}",
            """
            Dear {name},

            As requested, please find attached your invoice {invoiceNumber} for {amount}.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("name", "invoiceNumber", "amount")),

    CANDIDATE_INVITATION("candidate_invitation", "HR candidate — assessment invitation",
            "You've been invited to take an assessment — HEAIL",
            """
            Dear {candidateName},

            You've been invited to complete the following HEAIL assessment(s):

            {assessmentList}

            Each is 30 questions, timed at 30 minutes, completed in a single
            sitting — closing the browser or losing connection marks the
            attempt abandoned, so make sure you have an uninterrupted block of
            time before you begin.

            Start here (no account or password needed):
            {candidateLink}

            This link expires on {expiresAt} and cannot be renewed — if it lapses before
            you start, contact the person who invited you.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("candidateName", "assessmentList", "candidateLink", "expiresAt")),

    BUYER_CANDIDATE_EXPIRED("buyer_candidate_expired", "HR buyer — candidate window expired",
            "HEAIL — A candidate's assessment window has expired",
            """
            Dear {buyerName},

            {candidateName} did not start their assessment within the 7-day access window,
            and the link has now expired permanently. To assign this
            assessment to someone else, purchase a new test license.

            — Team HEAIL
            contact@heail.in
            """,
            List.of("buyerName", "candidateName"));

    private final String key;
    private final String label;
    private final String defaultSubject;
    private final String defaultBody;
    private final List<String> placeholders;

    EmailTemplateType(String key, String label, String defaultSubject, String defaultBody, List<String> placeholders) {
        this.key = key;
        this.label = label;
        this.defaultSubject = defaultSubject;
        this.defaultBody = defaultBody;
        this.placeholders = placeholders;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getDefaultSubject() { return defaultSubject; }
    public String getDefaultBody() { return defaultBody; }
    public List<String> getPlaceholders() { return placeholders; }

    private static final Map<String, EmailTemplateType> BY_KEY =
            Arrays.stream(values()).collect(Collectors.toMap(EmailTemplateType::getKey, Function.identity()));

    public static EmailTemplateType byKey(String key) {
        EmailTemplateType t = BY_KEY.get(key);
        if (t == null) throw new IllegalArgumentException("Unknown email template: " + key);
        return t;
    }
}
