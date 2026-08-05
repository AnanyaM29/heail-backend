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
    // PayPal is the only live gateway today and can't practically settle INR for an Indian
    // business account, so the whole commerce flow (draft price shown at agreement time,
    // and what's actually charged) runs in USD end-to-end. The ₹-denominated PricingItem
    // rows are left in place for display/future use, not read here.
    private static final String DEFAULT_CURRENCY = "USD";

    private final OrderRepository orderRepo;
    private final ConsentLogRepository consentRepo;
    private final UserRepository userRepo;
    private final PricingItemRepository pricingRepo;
    private final EmailService emailService;
    private final EntitlementRepository entitlementRepo;
    private final PaypalService paypalService;
    private final InvoiceService invoiceService;

    @Value("${app.payments.paypal-enabled:true}")
    private boolean paypalEnabled;

    /* ── Get the leader's open order, or start a new one ─────────── */
    @Transactional
    public OrderResponse getOrCreateDraftOrder(String email) {
        User user = requireUser(email);
        List<Order> existing = orderRepo.findByUserAndProductCodeOrderByDraftAtDesc(user, LEADER_CLASSIC_PRODUCT);
        Order latest = existing.isEmpty() ? null : existing.get(0);

        // Strictly DRAFT-only reuse: anything past that but never PAID means a prior
        // attempt was interrupted mid-flow (e.g. a capture failure), and reusing that
        // half-finished order would leave the caller stuck reusing a dead order instead
        // of being able to start a fresh attempt.
        boolean reusable = latest != null && latest.getStatus() == OrderStatus.DRAFT;

        if (reusable) return toResponse(latest);

        PricingItem pricing = pricingRepo.findByProductCodeAndCurrencyAndActiveTrue(LEADER_CLASSIC_PRODUCT, DEFAULT_CURRENCY)
                .orElseThrow(() -> new IllegalStateException("No active price configured for " + LEADER_CLASSIC_PRODUCT));

        BigDecimal gstAmount = pricing.getAmount()
                .multiply(pricing.getGstPct())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        Order order = new Order();
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

    /* ── Create a real PayPal order once the agreement is accepted ── */
    @Transactional
    public OrderResponse createPaypalOrder(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.AGREEMENT_ACCEPTED)
            throw new IllegalArgumentException("Accept the agreement before paying");

        if (!paypalEnabled) {
            log.warn("PayPal disabled — completing order {} without a real payment", order.getId());
            markPaidFromGateway(order, null);
            return toResponse(order);
        }

        BigDecimal total = order.getAmount().add(order.getGstAmount());
        String paypalOrderId = paypalService.createOrder(total, order.getCurrency(), order.getId().toString());

        order.setStatus(OrderStatus.PAYMENT_INITIATED);
        order.setPaymentInitiatedAt(LocalDateTime.now());
        order.setGatewayOrderRef(paypalOrderId);
        order = orderRepo.save(order);

        return toResponse(order);
    }

    /* ── Capture the PayPal order once the buyer approves it client-side ── */
    @Transactional
    public OrderResponse capturePaypalOrder(UUID orderId, String email, String paypalOrderId) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() == OrderStatus.PAID) return toResponse(order); // already fulfilled, e.g. by the webhook

        if (order.getStatus() != OrderStatus.PAYMENT_INITIATED || !paypalOrderId.equals(order.getGatewayOrderRef()))
            throw new IllegalArgumentException("No matching payment in progress for this order");

        PaypalService.PaypalCaptureResult result = paypalService.captureOrder(paypalOrderId);
        if (!result.completed()) {
            order.setStatus(OrderStatus.FAILED);
            orderRepo.save(order);
            throw new IllegalStateException("Payment was not completed (status: " + result.status() + ")");
        }

        markPaidFromGateway(order, result.captureId());
        return toResponse(order);
    }

    /**
     * Idempotent fulfilment — shared by the capture endpoint (immediate UX) and the
     * PayPal webhook (source of truth per spec §4A). Whichever arrives first does the
     * work; the second call is a no-op.
     *
     * Note: gatewayOrderRef is deliberately left holding the PayPal *order* ID (set at
     * createPaypalOrder time), not overwritten with the capture ID — the webhook looks
     * orders up by that field, and if this ran first it must stay lookup-able for the
     * webhook (or vice versa) regardless of which arrives first.
     */
    @Transactional
    public void markPaidFromGateway(Order order, String captureId) {
        if (order.getStatus() == OrderStatus.PAID) return;

        order.setStatus(OrderStatus.PAID);
        if (captureId != null) {
            Map<String, String> metadata = order.getMetadata() != null ? new HashMap<>(order.getMetadata()) : new HashMap<>();
            metadata.put("paypalCaptureId", captureId);
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
        return res;
    }
}
