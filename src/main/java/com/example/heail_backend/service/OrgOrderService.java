package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.exception.EmployeeBatchValidationException;
import com.example.heail_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrgOrderService {

    private static final String SUITE_4PULSE_PRODUCT = "SUITE_4PULSE";
    private static final String ORG_PURCHASE_AGREEMENT = "ORG_ADMIN_PURCHASE";
    private static final String INR_CURRENCY = "INR";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final List<String> VALID_LEVELS = List.of("L", "MM", "E");

    private final OrderRepository orderRepo;
    private final OrderEmployeeRepository orderEmployeeRepo;
    private final ConsentLogRepository consentRepo;
    private final UserRepository userRepo;
    private final OrganisationRepository orgRepo;
    private final PricingItemRepository pricingRepo;
    private final EmailService emailService;
    private final AuthService authService;
    private final PasswordEncoder encoder;
    private final InvoiceService invoiceService;
    private final OrgReportService orgReportService;
    private final RazorpayService razorpayService;

    @Value("${app.payments.razorpay-enabled:false}")
    private boolean razorpayEnabled;

    /** Razorpay is the only gateway now, so every order is priced in INR regardless of what's requested. */
    private String resolveCurrency(String requested, User user) {
        return INR_CURRENCY;
    }

    /**
     * Minimum sample size required on the employee-upload step, driven by the
     * organisation's declared headcount (captured at ORG_ADMIN registration):
     *   1–50     → all recommended, but at least 10 (capped at headcount)
     *   51–200   → at least 50%
     *   201–1000 → at least 40%
     *   1001+    → at least 30%
     */
    private static int minRequiredEmployees(int headcount) {
        if (headcount <= 50) return Math.min(10, Math.max(headcount, 1));
        double pct = headcount <= 200 ? 0.5 : headcount <= 1000 ? 0.4 : 0.3;
        return (int) Math.ceil(headcount * pct);
    }

    /* ── Get the org admin's open order, or start a new one ──────── */
    @Transactional
    public OrgOrderResponse getOrCreateDraftOrder(String email, String requestedCurrency) {
        User user = requireUser(email);
        // Employee/respondent accounts are added to a roster by an org admin — they
        // never buy or run their own organisation round themselves. Everyone else
        // (a fresh account, a Leader customer, an existing org admin, superadmin)
        // can, mirroring the rest of this class's isAuthenticated()-only gating.
        if ("EMPLOYEE".equals(user.getRole()))
            throw new AccessDeniedException("Employee accounts can't purchase an organisation round — ask your org admin.");
        String currency = resolveCurrency(requestedCurrency, user);
        List<Order> existing = orderRepo.findByUserAndProductCodeOrderByDraftAtDesc(user, SUITE_4PULSE_PRODUCT);
        Order latest = existing.isEmpty() ? null : existing.get(0);

        // Only a genuinely-DRAFT order is "still being edited" — anything that moved
        // past DRAFT (AGREEMENT_ACCEPTED, PAYMENT_INITIATED, etc.) but never reached
        // PAID means something interrupted the flow (e.g. a capture failure), and
        // reusing it just gets the caller permanently stuck. Start fresh instead.
        boolean reusable = latest != null && latest.getStatus() == OrderStatus.DRAFT;

        if (reusable && currency.equals(latest.getCurrency())) return toOrgResponse(latest);

        if (reusable) {
            // Buyer switched gateways before paying — re-price the same employee count
            // (if any) under the new currency instead of leaving a stale draft.
            int employeeCount = orderEmployeeRepo.findByOrder(latest).size();
            latest.setCurrency(currency);
            if (employeeCount > 0) {
                repriceForEmployeeCount(latest, employeeCount);
            } else {
                latest.setAmount(BigDecimal.ZERO);
                latest.setGstAmount(BigDecimal.ZERO);
            }
            Order saved = orderRepo.save(latest);
            return toOrgResponse(saved);
        }

        Order order = new Order();
        order.setUser(user);
        order.setProductCode(SUITE_4PULSE_PRODUCT);
        order.setAmount(BigDecimal.ZERO);
        order.setGstAmount(BigDecimal.ZERO);
        order.setCurrency(currency);
        order.setStatus(OrderStatus.DRAFT);
        order = orderRepo.save(order);
        return toOrgResponse(order);
    }

    @Transactional(readOnly = true)
    public OrgOrderResponse getOrder(UUID orderId, String email) {
        return toOrgResponse(requireOwnedOrder(orderId, email));
    }

    /* ── All rounds (draft or paid) the caller has created, most recent first ── */
    @Transactional(readOnly = true)
    public List<OrgOrderResponse> listMyOrders(String email) {
        User user = requireUser(email);
        return orderRepo.findByUserAndProductCodeOrderByDraftAtDesc(user, SUITE_4PULSE_PRODUCT).stream()
                .map(this::toOrgResponse).toList();
    }

    /* ── Replace the order's employee list, all-or-nothing ────────── */
    @Transactional
    public OrgOrderResponse setEmployees(UUID orderId, String email, List<EmployeeRowRequest> rows) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.DRAFT)
            throw new IllegalArgumentException("Employees can only be set on a draft order");

        Organisation organisation = order.getUser().getOrganisation();
        if (organisation == null || organisation.getHeadcount() == null)
            throw new IllegalStateException("Organisation headcount is not set on this account");
        int minRequired = minRequiredEmployees(organisation.getHeadcount());

        if (rows == null || rows.size() < minRequired)
            throw new IllegalArgumentException(
                    "Minimum " + minRequired + " employees required for your organisation size (" + (rows == null ? 0 : rows.size()) + " submitted)");

        List<RowError> errors = new ArrayList<>();
        List<String> seenEmails = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            EmployeeRowRequest row = rows.get(i);
            int rowNumber = i + 1;

            if (row.getName() == null || row.getName().isBlank()) {
                errors.add(new RowError(rowNumber, "Name is required"));
                continue;
            }
            if (row.getEmail() == null || !EMAIL_PATTERN.matcher(row.getEmail().trim()).matches()) {
                errors.add(new RowError(rowNumber, "A valid email is required"));
                continue;
            }
            String normalisedEmail = row.getEmail().trim().toLowerCase();
            if (seenEmails.contains(normalisedEmail)) {
                errors.add(new RowError(rowNumber, "Duplicate email within this submission"));
                continue;
            }
            String level = row.getLevel() == null ? "" : row.getLevel().trim().toUpperCase();
            if (!VALID_LEVELS.contains(level)) {
                errors.add(new RowError(rowNumber, "Level must be one of L, MM, E"));
                continue;
            }
            seenEmails.add(normalisedEmail);
        }

        if (!errors.isEmpty()) throw new EmployeeBatchValidationException(errors);

        orderEmployeeRepo.deleteByOrder(order);

        List<OrderEmployee> employees = new ArrayList<>();
        for (EmployeeRowRequest row : rows) {
            OrderEmployee emp = new OrderEmployee();
            emp.setOrder(order);
            emp.setName(row.getName().trim());
            emp.setEmail(row.getEmail().trim().toLowerCase());
            emp.setMobile(row.getMobile() == null || row.getMobile().isBlank() ? null : row.getMobile().trim());
            emp.setLevel(row.getLevel().trim().toUpperCase());
            employees.add(emp);
        }
        orderEmployeeRepo.saveAll(employees);

        repriceForEmployeeCount(order, employees.size());
        order = orderRepo.save(order);

        return toOrgResponse(order);
    }

    /** Recomputes amount/GST for the order's current currency and a given employee count. */
    private void repriceForEmployeeCount(Order order, int employeeCount) {
        PricingItem pricing = pricingRepo.findByProductCodeAndCurrencyAndActiveTrue(SUITE_4PULSE_PRODUCT, order.getCurrency())
                .orElseThrow(() -> new IllegalStateException("No active price configured for " + SUITE_4PULSE_PRODUCT + " in " + order.getCurrency()));

        BigDecimal amount = pricing.getAmount().multiply(BigDecimal.valueOf(employeeCount));
        BigDecimal gstAmount = amount.multiply(pricing.getGstPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        order.setAmount(amount);
        order.setGstAmount(gstAmount);
    }

    /**
     * Every account self-registers as a bare "LEADER" with no organisation —
     * there's only one type of registration (see AuthService.register()).
     * Organisation name, headcount and industry are all deferred to here, the
     * last step before the agreement/payment screens. First call creates the
     * Organisation and promotes the account to ORG_ADMIN; later calls on the
     * same draft order just update it (e.g. fixing a typo before paying).
     *
     * The promotion only applies to a plain self-registered LEADER — a
     * SUPERADMIN walking through this flow to set a round up on a client's
     * behalf must keep their SUPERADMIN role (the single-role-column design
     * means overwriting it here would silently lock them out of /admin).
     */
    @Transactional
    public OrgOrderResponse setOrgDetails(UUID orderId, String email, String organisationName,
                                           Integer headcount, String industry) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.DRAFT)
            throw new IllegalArgumentException("Organisation details can only be set on a draft order");

        if (organisationName == null || organisationName.isBlank())
            throw new IllegalArgumentException("Organisation name is required");
        if (headcount == null || headcount < 1)
            throw new IllegalArgumentException("Organisation headcount is required");

        User user = order.getUser();
        Organisation organisation = user.getOrganisation();
        boolean isNew = organisation == null;
        if (isNew) organisation = new Organisation();

        organisation.setName(organisationName);
        organisation.setHeadcount(headcount);
        if (industry != null && !industry.isBlank()) organisation.setIndustry(industry);
        organisation = orgRepo.save(organisation);

        if (isNew) {
            user.setOrganisation(organisation);
            if ("LEADER".equals(user.getRole())) user.setRole("ORG_ADMIN");
            userRepo.save(user);
        }

        return toOrgResponse(order);
    }

    /* ── Accept the Terms of Agreement for an order ───────────────── */
    @Transactional
    public OrgOrderResponse acceptAgreement(UUID orderId, String email, String version) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.DRAFT)
            throw new IllegalArgumentException("Agreement can only be accepted on a draft order");
        if (orderEmployeeRepo.findByOrder(order).isEmpty())
            throw new IllegalArgumentException("Add employees before accepting the agreement");

        order.setStatus(OrderStatus.AGREEMENT_ACCEPTED);
        order.setAgreementAcceptedAt(LocalDateTime.now());
        order = orderRepo.save(order);

        ConsentLog consent = new ConsentLog();
        consent.setUser(order.getUser());
        consent.setAgreementType(ORG_PURCHASE_AGREEMENT);
        consent.setVersion(version);
        consentRepo.save(consent);

        return toOrgResponse(order);
    }

    /* ── Create a real Razorpay order once the agreement is accepted ── */
    @Transactional
    public OrgOrderResponse createRazorpayOrder(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.AGREEMENT_ACCEPTED)
            throw new IllegalArgumentException("Accept the agreement before paying");

        if (!razorpayEnabled) {
            log.warn("Razorpay disabled — completing order {} without a real payment", order.getId());
            markPaidFromGateway(order, null);
            return toOrgResponse(order);
        }

        BigDecimal total = order.getAmount().add(order.getGstAmount());
        String razorpayOrderId = razorpayService.createOrder(total, order.getCurrency(), order.getId().toString());

        order.setStatus(OrderStatus.PAYMENT_INITIATED);
        order.setPaymentInitiatedAt(LocalDateTime.now());
        order.setGatewayOrderRef(razorpayOrderId);
        order = orderRepo.save(order);

        return toOrgResponse(order);
    }

    /* ── Verify the Razorpay payment signature Checkout.js hands back client-side ── */
    @Transactional
    public OrgOrderResponse verifyRazorpayPayment(UUID orderId, String email, String razorpayOrderId,
                                                   String razorpayPaymentId, String razorpaySignature) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() == OrderStatus.PAID) return toOrgResponse(order); // already fulfilled, e.g. by the webhook

        if (order.getStatus() != OrderStatus.PAYMENT_INITIATED || !razorpayOrderId.equals(order.getGatewayOrderRef()))
            throw new IllegalArgumentException("No matching payment in progress for this order");

        if (!razorpayService.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            order.setStatus(OrderStatus.FAILED);
            orderRepo.save(order);
            throw new IllegalStateException("Payment signature verification failed");
        }

        markPaidFromGateway(order, razorpayPaymentId);
        return toOrgResponse(order);
    }

    /**
     * Test-mode-only shortcut: completes the order without a real payment when the
     * caller just closes the Razorpay checkout overlay instead of paying. Gated on
     * the configured Razorpay key actually being a test key (rzp_test_*), so this
     * can never fire once the app is reconfigured with a live key for production.
     */
    @Transactional
    public OrgOrderResponse forceCompleteTestPayment(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (!razorpayService.isTestMode())
            throw new IllegalStateException("Live payments are enabled — this isn't available");
        if (order.getStatus() != OrderStatus.PAYMENT_INITIATED)
            throw new IllegalArgumentException("No payment in progress for this order");

        markPaidFromGateway(order, null);
        return toOrgResponse(order);
    }

    /**
     * Idempotent fulfilment — shared by the capture endpoint (immediate UX) and the
     * Razorpay webhook (source of truth per spec §4A). Whichever arrives first does the
     * work; the second call is a no-op.
     *
     * Note: gatewayOrderRef is deliberately left holding the Razorpay *order* ID (set at
     * createRazorpayOrder time), not overwritten with the payment ID — the webhook looks
     * orders up by that field, and if this ran first it must stay lookup-able for the
     * webhook (or vice versa) regardless of which arrives first.
     */
    @Transactional
    public void markPaidFromGateway(Order order, String paymentId) {
        if (order.getStatus() == OrderStatus.PAID) return;

        order.setStatus(OrderStatus.PAID);
        if (paymentId != null) {
            Map<String, String> metadata = order.getMetadata() != null ? new HashMap<>(order.getMetadata()) : new HashMap<>();
            metadata.put("razorpayPaymentId", paymentId);
            order.setMetadata(metadata);
        }
        order.setPaidAt(LocalDateTime.now());
        order.setInvoiceNumber(invoiceService.nextInvoiceNumber());
        order = orderRepo.save(order);

        List<OrderEmployee> employees = orderEmployeeRepo.findByOrder(order);
        fulfil(order, employees);

        BigDecimal total = order.getAmount().add(order.getGstAmount());
        String amountDisplay = order.getCurrency() + " " + total.setScale(2, RoundingMode.HALF_UP);
        byte[] invoicePdf = invoiceService.generate(order, order.getUser().getName(), order.getUser().getEmail());
        emailService.sendOrgPaymentSuccess(order.getUser().getEmail(), order.getUser().getName(),
                amountDisplay, employees.size(), order.getGatewayOrderRef(), invoicePdf, order.getInvoiceNumber());
    }

    @Transactional
    public void markFailedFromGateway(Order order) {
        if (order.getStatus() == OrderStatus.PAID) return;
        order.setStatus(OrderStatus.FAILED);
        orderRepo.save(order);
    }

    /* ── Cancel a round before it's paid for ──────────────────────── */
    @Transactional
    public OrgOrderResponse cancelOrder(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() == OrderStatus.PAID)
            throw new IllegalArgumentException("This round has already been paid for and can't be cancelled");

        order.setStatus(OrderStatus.ABANDONED);
        order = orderRepo.save(order);
        return toOrgResponse(order);
    }

    /* ── Employee×Pulse progress grid for the org admin (no scores) ──── */
    @Transactional(readOnly = true)
    public OrgMonitorResponse getMonitor(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        return orgReportService.buildMonitor(order);
    }

    /* ── RAG report, only once released ────────────────────────────── */
    @Transactional(readOnly = true)
    public OrgReportResponse getReport(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getReportReleasedAt() == null)
            throw new IllegalStateException("Report has not been released yet for this round");
        return orgReportService.buildReport(order);
    }

    /* ── Create/link a User per employee row, dispatch OTP + invitation ── */
    private void fulfil(Order order, List<OrderEmployee> employees) {
        Organisation organisation = order.getUser().getOrganisation();

        for (OrderEmployee emp : employees) {
            User user = userRepo.findByEmail(emp.getEmail()).orElse(null);

            if (user == null) {
                user = new User();
                user.setName(emp.getName());
                user.setEmail(emp.getEmail());
                user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
                user.setRole("EMPLOYEE");
                user.setOrganisation(organisation);
                user.setRespondentLevel(emp.getLevel());
                user = userRepo.save(user);
            }
            // NOTE: if the user already exists (e.g. previously registered as LEADER or ORG_ADMIN),
            // we deliberately leave role/organisation/respondentLevel untouched — the single-role-column
            // design means a user can only hold one role platform-wide today, and overwriting it here
            // would break their existing login routing. Known limitation, not fixed in this pass.

            emp.setUser(user);

            try {
                ForgotPasswordRequest fpReq = new ForgotPasswordRequest();
                fpReq.setEmail(user.getEmail());
                authService.forgotPassword(fpReq);
                emailService.sendEmployeeInvitation(user.getEmail(), emp.getName(),
                        organisation != null ? organisation.getName() : "your organisation");
                emp.setInvitationStatus("SENT");
            } catch (Exception e) {
                log.error("Failed to dispatch invitation to {}: {}", emp.getEmail(), e.getMessage());
                emp.setInvitationStatus("UNDELIVERABLE");
            }
            orderEmployeeRepo.save(emp);
        }
    }

    /* ── Private helpers ───────────────────────────────────────── */
    private Order requireOwnedOrder(UUID orderId, String email) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getUser().getEmail().equalsIgnoreCase(email))
            throw new IllegalArgumentException("Order not found");
        return order;
    }

    private User requireUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private OrgOrderResponse toOrgResponse(Order order) {
        OrgOrderResponse res = new OrgOrderResponse();
        res.setOrder(toOrderResponse(order));
        res.setEmployees(orderEmployeeRepo.findByOrder(order).stream().map(this::toEmployeeDto).toList());

        Organisation organisation = order.getUser().getOrganisation();
        if (organisation != null) {
            res.setOrgName(organisation.getName());
            res.setOrgHeadcount(organisation.getHeadcount());
            res.setOrgIndustry(organisation.getIndustry());
            if (organisation.getHeadcount() != null)
                res.setMinEmployeesRequired(minRequiredEmployees(organisation.getHeadcount()));
        }
        return res;
    }

    private OrderResponse toOrderResponse(Order order) {
        OrderResponse res = new OrderResponse();
        res.setId(order.getId());
        res.setProduct(order.getProductCode());
        res.setBaseAmount(order.getAmount());
        res.setGstAmount(order.getGstAmount());
        res.setTotalAmount(order.getAmount().add(order.getGstAmount()));
        res.setCurrency(order.getCurrency());
        res.setStatus(order.getStatus().name());
        res.setAgreementAcceptedAt(order.getAgreementAcceptedAt());
        res.setGatewayReference(order.getGatewayOrderRef());
        res.setInvoiceNumber(order.getInvoiceNumber());
        res.setPaidAt(order.getPaidAt());
        res.setCreatedAt(order.getDraftAt());
        res.setMetadata(order.getMetadata());
        res.setRazorpayKeyId(razorpayService.getKeyId());
        return res;
    }

    private OrderEmployeeDto toEmployeeDto(OrderEmployee emp) {
        OrderEmployeeDto dto = new OrderEmployeeDto();
        dto.setId(emp.getId());
        dto.setName(emp.getName());
        dto.setEmail(emp.getEmail());
        dto.setMobile(emp.getMobile());
        dto.setLevel(emp.getLevel());
        dto.setInvitationStatus(emp.getInvitationStatus());
        return dto;
    }
}
