package com.example.heail_backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AdminPaymentDto {
    UUID id;
    String userName;
    String userEmail;
    String productCode;
    String status;
    String currency;
    BigDecimal baseAmount;
    BigDecimal gstAmount;
    BigDecimal totalAmount;
    String gatewayOrderRef;
    String invoiceNumber;
    LocalDateTime draftAt;
    LocalDateTime paidAt;
}
