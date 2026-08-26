package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DiscountCouponDto {
    UUID id;
    String code;
    int discountPercent;
    boolean active;
    String createdBy;
    LocalDateTime createdAt;
    LocalDateTime expiresAt;
    String sentToEmail;
    LocalDateTime usedAt;
    UUID usedByOrderId;
    String usedByEmail;
}
