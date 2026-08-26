package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/** A superadmin-generated coupon: one 8-character code good for a 0-100%
 *  discount, redeemable once by any user against any of the three products. */
@Entity
@Table(name = "discount_coupons")
@Data
public class DiscountCoupon {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @Column(nullable = false, unique = true, length = 8)
    String code;

    @Column(name = "discount_percent", nullable = false)
    int discountPercent;

    @Column(nullable = false)
    boolean active = true;

    @Column(name = "created_by", nullable = false)
    String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    // Every coupon is good for exactly 7 days from creation, win or lose — set once here
    // (see prePersist), never extended. Nullable at the DB level only so ddl-auto=update can
    // add this column to a table that already has rows from before this field existed — every
    // coupon created going forward always gets a value; see the null-safe check in
    // DiscountCouponService.applyToOrder for rows that predate it.
    @Column(name = "expires_at", updatable = false)
    LocalDateTime expiresAt;

    // Set when the admin sends this code to a specific person at generation time — if
    // present, only that email can redeem it (see DiscountCouponService.applyToOrder).
    @Column(name = "sent_to_email")
    String sentToEmail;

    @Column(name = "used_at")
    LocalDateTime usedAt;

    @Column(name = "used_by_order_id")
    UUID usedByOrderId;

    @Column(name = "used_by_email")
    String usedByEmail;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.expiresAt = this.createdAt.plusDays(7);
    }
}
