package com.example.heail_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateCouponRequest {
    @NotNull
    @Min(0)
    @Max(100)
    Integer discountPercent;

    // Optional — if set, the coupon is emailed to this address on generation and can only
    // ever be redeemed by that same email.
    @Email
    String email;
}
