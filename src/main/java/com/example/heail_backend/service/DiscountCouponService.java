package com.example.heail_backend.service;

import com.example.heail_backend.dto.DiscountCouponDto;
import com.example.heail_backend.entity.DiscountCoupon;
import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.OrderStatus;
import com.example.heail_backend.repository.DiscountCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared across all three products (Leader/Org/HR each keep their own
 * OrderService-shaped class, but they all operate on the same Order table —
 * see class docs on OrgOrderService/HrOrderService). A coupon is redeemed by
 * mutating the order's amount/gstAmount directly, scaled down by the discount
 * percentage, so every existing "amount + gstAmount" total computation
 * (payment screens, Razorpay order creation, invoicing) picks up the discount
 * without any changes elsewhere.
 */
@Service
@RequiredArgsConstructor
public class DiscountCouponService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DiscountCouponRepository couponRepo;
    private final EmailService emailService;

    @Transactional
    public DiscountCouponDto generate(int discountPercent, String adminEmail, String sendToEmail) {
        if (discountPercent < 0 || discountPercent > 100)
            throw new IllegalArgumentException("Discount must be between 0 and 100");

        DiscountCoupon coupon = new DiscountCoupon();
        coupon.setCode(generateUniqueCode());
        coupon.setDiscountPercent(discountPercent);
        coupon.setActive(true);
        coupon.setCreatedBy(adminEmail);
        if (sendToEmail != null && !sendToEmail.isBlank()) coupon.setSentToEmail(sendToEmail.trim().toLowerCase());
        coupon = couponRepo.save(coupon);

        if (coupon.getSentToEmail() != null)
            emailService.sendCouponCode(coupon.getSentToEmail(), coupon.getCode(), coupon.getDiscountPercent(), coupon.getExpiresAt());

        return toDto(coupon);
    }

    @Transactional(readOnly = true)
    public List<DiscountCouponDto> list() {
        return couponRepo.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public void revoke(String code) {
        DiscountCoupon coupon = couponRepo.findByCode(normalise(code))
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));
        if (coupon.getUsedAt() != null)
            throw new IllegalArgumentException("This coupon has already been redeemed and can't be revoked");
        coupon.setActive(false);
        couponRepo.save(coupon);
    }

    /**
     * Redeems a coupon against an order still in DRAFT (i.e. before the agreement
     * is accepted — the "before you pay" step). Mutates amount/gstAmount in place;
     * caller is responsible for saving the order. Throws if the code is unknown,
     * inactive, already used, or the order isn't eligible.
     */
    @Transactional
    public DiscountCouponDto applyToOrder(Order order, String code, String userEmail) {
        if (order.getStatus() != OrderStatus.DRAFT)
            throw new IllegalArgumentException("A coupon can only be applied before the agreement is accepted");
        if (order.getCouponCode() != null)
            throw new IllegalArgumentException("A coupon has already been applied to this order");

        DiscountCoupon coupon = couponRepo.findByCode(normalise(code))
                .orElseThrow(() -> new IllegalArgumentException("Invalid coupon code"));
        if (!coupon.isActive())
            throw new IllegalArgumentException("This coupon is no longer active");
        if (coupon.getUsedAt() != null)
            throw new IllegalArgumentException("This coupon has already been used");
        if (coupon.getExpiresAt() != null && LocalDateTime.now().isAfter(coupon.getExpiresAt()))
            throw new IllegalArgumentException("This coupon has expired");
        if (coupon.getSentToEmail() != null && !coupon.getSentToEmail().equalsIgnoreCase(userEmail))
            throw new IllegalArgumentException("This coupon is not valid for your account");

        BigDecimal factor = BigDecimal.valueOf(100 - coupon.getDiscountPercent())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        order.setAmount(order.getAmount().multiply(factor).setScale(2, RoundingMode.HALF_UP));
        order.setGstAmount(order.getGstAmount().multiply(factor).setScale(2, RoundingMode.HALF_UP));
        order.setCouponCode(coupon.getCode());
        order.setDiscountPercent(coupon.getDiscountPercent());

        // A used coupon is expired in every sense that matters — active flips off too, not
        // just usedAt, so the admin list and any future active-only lookup agree on that.
        coupon.setUsedAt(LocalDateTime.now());
        coupon.setUsedByOrderId(order.getId());
        coupon.setUsedByEmail(userEmail);
        coupon.setActive(false);
        couponRepo.save(coupon);

        return toDto(coupon);
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            code = sb.toString();
        } while (couponRepo.existsByCode(code));
        return code;
    }

    private String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private DiscountCouponDto toDto(DiscountCoupon c) {
        DiscountCouponDto dto = new DiscountCouponDto();
        dto.setId(c.getId());
        dto.setCode(c.getCode());
        dto.setDiscountPercent(c.getDiscountPercent());
        dto.setActive(c.isActive());
        dto.setCreatedBy(c.getCreatedBy());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setExpiresAt(c.getExpiresAt());
        dto.setSentToEmail(c.getSentToEmail());
        dto.setUsedAt(c.getUsedAt());
        dto.setUsedByOrderId(c.getUsedByOrderId());
        dto.setUsedByEmail(c.getUsedByEmail());
        return dto;
    }
}
