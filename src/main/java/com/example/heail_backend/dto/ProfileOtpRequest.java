package com.example.heail_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** purpose: one of CURRENT / NEW_EMAIL / NEW_MOBILE.
 *  target: only meaningful (and required) for NEW_EMAIL — the prospective new address. */
@Data
public class ProfileOtpRequest {
    @NotBlank
    String purpose;

    String target;
}
