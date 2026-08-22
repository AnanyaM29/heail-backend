package com.example.heail_backend.service;

import com.example.heail_backend.dto.OrderResponse;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Purchase flow for the HR Competency Assessment product — pick any subset
 * of the 7 pillars, pay once, self-serve — same "anyone can buy it" shape
 * as OrderService/Leader, not a bulk org-roster round like OrgOrderService.
 * The one real difference from Leader: this order can grant *multiple*
 * entitlements (one per selected pillar) rather than exactly one, so the
 * selection has to be captured somewhere on the order and read back at
 * fulfilment time — done via Order.metadata (see SELECTED_IDS_KEY), the
 * same free-form JSON column Leader already uses for designation/org name.
 *
 * Deliberately duplicated from OrderService rather than generalizing it —
 * same reasoning as OrgOrderService already being its own class: each
 * purchase shape's fulfilment differs enough (here: N entitlements from one
 * order, not one) that sharing the state-machine code would mean threading
 * product-specific branches through every method anyway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrOrderService {

    private static final String HR_SUITE_PRODUCT = "HR_SUITE";
    private static final String HR_PILLAR_PRICING_CODE = "HR_PILLAR"; // per-pillar unit price lookup
    private static final String HR_PURCHASE_AGREEMENT = "HR_PURCHASE";
    private static final String INR_CURRENCY = "INR";
    private static final String SELECTED_IDS_KEY = "selectedAssessmentIds";
    private static final String ENTITLEMENT_PREFIX = "HR_A";

    private final OrderRepository orderRepo;
    private final ConsentLogRepository consentRepo;
    private final UserRepository userRepo;
    private final PricingItemRepository pricingRepo;
    private final EmailService emailService;
    private final EntitlementRepository entitlementRepo;
    private final RazorpayService razorpayService;
    private final InvoiceService invoiceService;
    private final HrAssessmentRepository hrAssessmentRepo;

    @Value("${app.payments.razorpay-enabled:false}")
    private boolean razorpayEnabled;

    /* ── Set (or change) which pillars this order covers ──────────── */
    @Transactional
    public OrderResponse selectAssessments(String email, List<Short> assessmentIds) {
        if (assessmentIds == null || assessmentIds.isEmpty())
            throw new IllegalArgumentException("Select at least one assessment");
        List<Short> distinctIds = assessmentIds.stream().distinct().sorted().toList();

        List<HrAssessment> found = hrAssessmentRepo.findAllById(distinctIds);
        if (found.size() != distinctIds.size())
            throw new IllegalArgumentException("One or more selected assessments don't exist");

        User user = requireUser(email);
        List<Order> existing = orderRepo.findByUserAndProductCodeOrderByDraftAtDesc(user, HR_SUITE_PRODUCT);
        Order latest = existing.isEmpty() ? null : existing.get(0);

        // Same reuse rule as Leader: only ever reuse a still-DRAFT order. Anything
        // further along but never PAID is treated as a dead, interrupted attempt.
        boolean reusable = latest != null && latest.getStatus() == OrderStatus.DRAFT;

        PricingItem pricing = pricingRepo.findByProductCodeAndCurrencyAndActiveTrue(HR_PILLAR_PRICING_CODE, INR_CURRENCY)
                .orElseThrow(() -> new IllegalStateException("No active price configured for " + HR_PILLAR_PRICING_CODE));

        BigDecimal amount = pricing.getAmount().multiply(BigDecimal.valueOf(distinctIds.size()));
        BigDecimal gstAmount = amount.multiply(pricing.getGstPct())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        Order order = reusable ? latest : new Order();
        order.setUser(user);
        order.setProductCode(HR_SUITE_PRODUCT);
        order.setAmount(amount);
        order.setGstAmount(gstAmount);
        order.setCurrency(pricing.getCurrency());
        order.setStatus(OrderStatus.DRAFT);

        Map<String, String> metadata = order.getMetadata() != null ? new HashMap<>(order.getMetadata()) : new HashMap<>();
        metadata.put(SELECTED_IDS_KEY, serializeIds(distinctIds));
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
        consent.setAgreementType(HR_PURCHASE_AGREEMENT);
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

    /** Test-mode-only shortcut — same gate as OrderService: only fires for rzp_test_* keys. */
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
     * Idempotent fulfilment — shared by the capture endpoint and the Razorpay webhook,
     * same pattern as OrderService.markPaidFromGateway. The one real difference: this
     * grants one Entitlement *per selected pillar*, not a single entitlement matching
     * the order's own product code (HR_SUITE is the order's product; HR_A{n} is what
     * actually gates starting a given pillar — see HrAssessmentService).
     */
    @Transactional
    public void markPaidFromGateway(Order order, String paymentId) {
        if (order.getStatus() == OrderStatus.PAID) return;

        order.setStatus(OrderStatus.PAID);
        Map<String, String> metadata = order.getMetadata() != null ? new HashMap<>(order.getMetadata()) : new HashMap<>();
        if (paymentId != null) metadata.put("razorpayPaymentId", paymentId);
        order.setMetadata(metadata);
        order.setPaidAt(LocalDateTime.now());
        order.setInvoiceNumber(invoiceService.nextInvoiceNumber());
        order = orderRepo.save(order);

        for (Short assessmentId : deserializeIds(metadata.get(SELECTED_IDS_KEY))) {
            Entitlement entitlement = new Entitlement();
            entitlement.setUser(order.getUser());
            entitlement.setProductCode(ENTITLEMENT_PREFIX + assessmentId);
            entitlement.setSource(EntitlementSource.PURCHASE);
            entitlement.setOrder(order);
            entitlement.setUsed(false);
            entitlementRepo.save(entitlement);
        }

        BigDecimal total = order.getAmount().add(order.getGstAmount());
        String amountDisplay = order.getCurrency() + " " + total.setScale(2, RoundingMode.HALF_UP);
        byte[] invoicePdf = invoiceService.generate(order, order.getUser().getName(), order.getUser().getEmail());
        emailService.sendInvoice(order.getUser().getEmail(), order.getUser().getName(),
                amountDisplay, invoicePdf, order.getInvoiceNumber());
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
    private String serializeIds(List<Short> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Short> deserializeIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(Short::parseShort).toList();
    }

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
