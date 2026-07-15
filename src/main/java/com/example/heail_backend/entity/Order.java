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

    @Column(name = "draft_at", nullable = false, updatable = false)
    LocalDateTime draftAt;

    @Column(name = "agreement_accepted_at")
    LocalDateTime agreementAcceptedAt;

    @Column(name = "payment_initiated_at")
    LocalDateTime paymentInitiatedAt;

    @Column(name = "paid_at")
    LocalDateTime paidAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    Map<String, String> metadata;

    @PrePersist
    void prePersist() {
        this.draftAt = LocalDateTime.now();
    }
}
