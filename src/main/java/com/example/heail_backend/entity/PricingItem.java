package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_items")
@IdClass(PricingItemId.class)
@Data
public class PricingItem {

    @Id
    @Column(name = "product_code")
    String productCode;

    @Id
    @Column(name = "currency")
    String currency;

    @Column(nullable = false)
    BigDecimal amount;

    @Column(name = "gst_pct", nullable = false)
    BigDecimal gstPct;

    @Column(nullable = false)
    boolean active;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
