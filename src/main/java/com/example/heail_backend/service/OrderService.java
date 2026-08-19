package com.example.heail_backend.service;

import com.example.heail_backend.dto.OrderResponse;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.ConsentLogRepository;
import com.example.heail_backend.repository.EntitlementRepository;
import com.example.heail_backend.repository.OrderRepository;
import com.example.heail_backend.repository.PricingItemRepository;
import com.example.heail_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String LEADER_CLASSIC_PRODUCT = "LEADER_CLASSIC";
    private static final String LEADER_PURCHASE_AGREEMENT = "LEADER_PURCHASE";
    private static final String INR_CURRENCY = "INR";

    private final OrderRepository orderRepo;
    private final ConsentLogRepository consentRepo;
    private final UserRepository userRepo;
    private final PricingItemRepository pricingRepo;
    private final EmailService emailService;
    private final EntitlementRepository entitlementRepo;
    private final RazorpayService razorpayService;
    private final InvoiceService invoiceService;

    @Value("${app.payments.razorpay-enabled:false}")
    private boolean razorpayEnabled;

    /** Razorpay is the only gateway now, so every order is priced in INR regardless of what's requested. */
    private String resolveCurrency(String requested, User user) {
        return INR_CURRENCY;
    }

    /* ── Get the leader's open order, or start a new one ─────────── */
    @Transactional
    public OrderResponse getOrCreateDraftOrder(String email, String requestedCurrency) {
        User user = requireUser(email);
        String currency = resolveCurrency(requestedCurrency, user);
        List<Order> existing = orderRepo.findByUserAndProductCodeOrderByDraftAtDesc(user, LEADER_CLASSIC_PRODUCT);
        Order latest = existing.isEmpty() ? null : existing.get(0);

        // Strictly DRAFT-only reuse: anything past that but never PAID means a prior
        // attempt was interrupted mid-flow (e.g. a capture failure), and reusing that
        // half-finished order would leave the caller stuck reusing a dead order instead
        // of being able to start a fresh attempt.
        boolean reusable = latest != null && latest.getStatus() == OrderStatus.DRAFT;

        if (reusable && currency.equals(latest.getCurrency())) return toResponse(latest);

        PricingItem pricing = pricingRepo.findByProductCodeAndCurrencyAndActiveTrue(LEADER_CLASSIC_PRODUCT, currency)
                .orElseThrow(() -> new IllegalStateException("No active price configured for " + LEADER_CLASSIC_PRODUCT + " in " + currency));

        BigDecimal gstAmount = pricing.getAmount()
                .multiply(pricing.getGstPct())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // Reusable draft but a different currency was requested (e.g. the buyer switched
        // gateways before paying) — update it in place rather than leaving a stale draft
        // and creating a second one.
        Order order = reusable ? latest : new Order();
        order.setUser(user);
        order.setProductCode(LEADER_CLASSIC_PRODUCT);
        order.setAmount(pricing.getAmount());
        order.setGstAmount(gstAmount);
        order.setCurrency(pricing.getCurrency());
        order.setStatus(OrderStatus.DRAFT);
        order = orderRepo.save(order);
        return toResponse(order);
    }

    /* ── Optional purchase-time context (designation / organisation) ── */
    @Transactional
    public OrderResponse updateDetails(UUID orderId, String email, String designation, String organisationName) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.DRAFT)
            throw new IllegalArgumentException("Details can only be set on a draft order");

        Map<String, String> metadata = new HashMap<>();
        if (designation != null && !designation.isBlank()) metadata.put("designation", designation.trim());
        if (organisationName != null && !organisationName.isBlank()) metadata.put("organisationName", organisationName.trim());
        order.setMetadata(metadata);
        order = orderRepo.save(order);

        return toResponse(order);
    }

    /* ── Accept the Terms of Agreement for an order ───────────────── */
    @Transactional
    public OrderResponse acceptAgreement(UUID orderId, String email, String version) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.DRAFT)
            throw new IllegalArgumentException("Agreement can only be accepted on a draft order");

        order.setStatus(OrderStatus.AGREEMENT_ACCEPTED);
        order.setAgreementAcceptedAt(LocalDateTime.now());
        order = orderRepo.save(order);

        ConsentLog consent = new ConsentLog();
        consent.setUser(order.getUser());
        consent.setAgreementType(LEADER_PURCHASE_AGREEMENT);
        consent.setVersion(version);
        consentRepo.save(consent);

        return toResponse(order);
    }

    /* ── Create a real Razorpay order once the agreement is accepted ── */
    @Transactional
    public OrderResponse createRazorpayOrder(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.AGREEMENT_ACCEPTED)
            throw new IllegalArgumentException("Accept the agreement before paying");

        if (!razorpayEnabled) {
            log.warn("Razorpay disabled — completing order {} without a real payment", order.getId());
            markPaidFromGateway(order, null);
            return toResponse(order);
        }

        BigDecimal total = order.getAmount().add(order.getGstAmount());
        String razorpayOrderId = razorpayService.createOrder(total, order.getCurrency(), order.getId().toString());

        order.setStatus(OrderStatus.PAYMENT_INITIATED);
        order.setPaymentInitiatedAt(LocalDateTime.now());
        order.setGatewayOrderRef(razorpayOrderId);
        order = orderRepo.save(order);

        return toResponse(order);
    }

    /* ── Verify the Razorpay payment signature Checkout.js hands back client-side ── */
    @Transactional
    public OrderResponse verifyRazorpayPayment(UUID orderId, String email, String razorpayOrderId,
                                                String razorpayPaymentId, String razorpaySignature) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() == OrderStatus.PAID) return toResponse(order); // already fulfilled, e.g. by the webhook

        if (order.getStatus() != OrderStatus.PAYMENT_INITIATED || !razorpayOrderId.equals(order.getGatewayOrderRef()))
            throw new IllegalArgumentException("No matching payment in progress for this order");

        if (!razorpayService.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            order.setStatus(OrderStatus.FAILED);
            orderRepo.save(order);
            throw new IllegalStateException("Payment signature verification failed");
        }

        markPaidFromGateway(order, razorpayPaymentId);
        return toResponse(order);
    }

    /**
     * Test-mode-only shortcut: completes the order without a real payment when the
     * caller just closes the Razorpay checkout overlay instead of paying. Gated on
     * the configured Razorpay key actually being a test key (rzp_test_*), so this
     * can never fire once the app is reconfigured with a live key for production.
     */
    @Transactional
    public OrderResponse forceCompleteTestPayment(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (!razorpayService.isTestMode())
            throw new IllegalStateException("Live payments are enabled — this isn't available");
        if (order.getStatus() != OrderStatus.PAYMENT_INITIATED)
            throw new IllegalArgumentException("No payment in progress for this order");

        markPaidFromGateway(order, null);
        return toResponse(order);
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

        Entitlement entitlement = new Entitlement();
        entitlement.setUser(order.getUser());
        entitlement.setProductCode(order.getProductCode());
        entitlement.setSource(EntitlementSource.PURCHASE);
        entitlement.setOrder(order);
        entitlement.setUsed(false);
        entitlementRepo.save(entitlement);

        BigDecimal total = order.getAmount().add(order.getGstAmount());
        String amountDisplay = order.getCurrency() + " " + total.setScale(2, RoundingMode.HALF_UP);
        byte[] invoicePdf = invoiceService.generate(order, order.getUser().getName(), order.getUser().getEmail());
        emailService.sendLeaderPaymentSuccess(order.getUser().getEmail(), order.getUser().getName(),
                amountDisplay, order.getGatewayOrderRef(), invoicePdf, order.getInvoiceNumber());
    }

    @Transactional
    public void markFailedFromGateway(Order order) {
        if (order.getStatus() == OrderStatus.PAID) return;
        order.setStatus(OrderStatus.FAILED);
        orderRepo.save(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, String email) {
        return toResponse(requireOwnedOrder(orderId, email));
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

    private OrderResponse toResponse(Order order) {
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
}
