package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
public class Order {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "product_code", nullable = false)
    String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    OrderStatus status;

    @Column(nullable = false)
    String currency;

    @Column(nullable = false)
    BigDecimal amount;

    @Column(name = "gst_amount", nullable = false)
    BigDecimal gstAmount;

    @Column(name = "gateway_order_ref")
    String gatewayOrderRef;

    @Column(name = "invoice_number")
    String invoiceNumber;

    // Set together when a coupon is redeemed on this order (see
    // DiscountCouponService.applyToOrder) — amount/gstAmount are mutated in place
    // to the discounted values at that point, so every existing total-computation
    // call site (amount + gstAmount) picks up the discount with no further changes.
    @Column(name = "coupon_code")
    String couponCode;

    @Column(name = "discount_percent")
    Integer discountPercent;

    @Column(name = "draft_at", nullable = false, updatable = false)
    LocalDateTime draftAt;

    @Column(name = "agreement_accepted_at")
    LocalDateTime agreementAcceptedAt;

    @Column(name = "payment_initiated_at")
    LocalDateTime paymentInitiatedAt;

    @Column(name = "paid_at")
    LocalDateTime paidAt;

    @Column(name = "report_released_at")
    LocalDateTime reportReleasedAt;

    @Column(name = "last_non_starter_email_at")
    LocalDateTime lastNonStarterEmailAt;

    @Column(name = "last_status_email_at")
    LocalDateTime lastStatusEmailAt;

    @Column(name = "day8_email_sent_at")
    LocalDateTime day8EmailSentAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    Map<String, String> metadata;

    /** The organisation THIS round is for — set once on the first setOrgDetails()
     *  call for this order (see OrgOrderService). Deliberately separate from
     *  User.organisation: one HEAIL account (e.g. a superadmin, or a consultant)
     *  can set up rounds for more than one client company, and each round must
     *  keep showing its own company name forever, even after the account goes on
     *  to set up a differently-named round later. Null on orders created before
     *  this field existed — callers fall back to user.getOrganisation() for those. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    Organisation organisation;

    @PrePersist
    void prePersist() {
        this.draftAt = LocalDateTime.now();
    }

    /** This order's own organisation if it has one, else the buyer account's —
     *  the fallback only matters for orders created before the `organisation`
     *  column existed. Every read of "which company is this round for" should
     *  go through here, never through user.getOrganisation() directly, or a
     *  later round under the same account can rename an earlier one's company. */
    public Organisation effectiveOrganisation() {
        return organisation != null ? organisation : (user != null ? user.getOrganisation() : null);
    }
}
