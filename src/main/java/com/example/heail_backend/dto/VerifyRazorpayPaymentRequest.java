package com.example.heail_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyRazorpayPaymentRequest {

    @NotBlank
    String razorpayOrderId;

    @NotBlank
    String razorpayPaymentId;

    @NotBlank
    String razorpaySignature;
}
