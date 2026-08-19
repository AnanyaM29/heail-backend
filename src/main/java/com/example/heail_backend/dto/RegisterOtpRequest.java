package com.example.heail_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterOtpRequest {
    @NotBlank @Email
    String email;

    // Optional — the registration form may not have a valid mobile filled in yet
    // when "send code" is clicked. When present, the OTP also goes out by SMS.
    String mobile;
}
