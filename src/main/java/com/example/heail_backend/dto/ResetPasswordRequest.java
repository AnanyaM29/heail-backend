package com.example.heail_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank @Email
    String email;

    @NotBlank @Size(min = 6, max = 6)
    String otp;

    @NotBlank @Size(min = 8)
    String newPassword;
}
