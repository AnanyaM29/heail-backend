package com.example.heail_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyProfileOtpRequest {
    @NotBlank
    String purpose;

    String target;

    @NotBlank @Size(min = 6, max = 6)
    String otp;
}
