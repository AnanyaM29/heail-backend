package com.example.heail_backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class OrderResponse {
    UUID id;
    String product;
    BigDecimal baseAmount;
    BigDecimal gstAmount;
    BigDecimal totalAmount;
    String currency;
    String status;
    LocalDateTime agreementAcceptedAt;
    String gatewayReference;
    LocalDateTime paidAt;
    LocalDateTime createdAt;
    Map<String, String> metadata;
}
