package com.example.heail_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PartnerOtpRequest {
    @NotBlank @Email
    String email;

    // Optional — same reasoning as RegisterOtpRequest: mobile may not be filled
    // in yet when "send code" is clicked, so the SMS leg is best-effort.
    String mobile;
}
