package com.example.heail_backend.service;

import com.example.heail_backend.dto.OrderResponse;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.ConsentLogRepository;
import com.example.heail_backend.repository.EntitlementRepository;
import com.example.heail_backend.repository.OrderRepository;
import com.example.heail_backend.repository.PricingItemRepository;
import com.example.heail_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String LEADER_CLASSIC_PRODUCT = "LEADER_CLASSIC";
    private static final String LEADER_PURCHASE_AGREEMENT = "LEADER_PURCHASE";
    private static final String DEFAULT_CURRENCY = "INR";

    private final OrderRepository orderRepo;
    private final ConsentLogRepository consentRepo;
    private final UserRepository userRepo;
    private final PricingItemRepository pricingRepo;
    private final EmailService emailService;
    private final EntitlementRepository entitlementRepo;

    /* ── Get the leader's open order, or start a new one ─────────── */
    @Transactional
    public OrderResponse getOrCreateDraftOrder(String email) {
        User user = requireUser(email);
        List<Order> existing = orderRepo.findByUserAndProductCodeOrderByDraftAtDesc(user, LEADER_CLASSIC_PRODUCT);
        Order latest = existing.isEmpty() ? null : existing.get(0);

        boolean reusable = latest != null
                && latest.getStatus() != OrderStatus.PAID
                && latest.getStatus() != OrderStatus.FAILED
                && latest.getStatus() != OrderStatus.ABANDONED;

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

    /* ── Mock payment (stands in for the real gateway of §13) ────── */
    @Transactional
    public OrderResponse payMock(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.AGREEMENT_ACCEPTED)
            throw new IllegalArgumentException("Accept the agreement before paying");

        order.setStatus(OrderStatus.PAYMENT_INITIATED);
        order.setPaymentInitiatedAt(LocalDateTime.now());
        orderRepo.save(order);

        order.setStatus(OrderStatus.PAID);
        order.setGatewayOrderRef("MOCK-" + UUID.randomUUID());
        order.setPaidAt(LocalDateTime.now());
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
        emailService.sendLeaderPaymentSuccess(order.getUser().getEmail(), order.getUser().getName(),
                amountDisplay, order.getGatewayOrderRef());

        return toResponse(order);
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
        res.setPaidAt(order.getPaidAt());
        res.setCreatedAt(order.getDraftAt());
        res.setMetadata(order.getMetadata());
        return res;
    }
}
