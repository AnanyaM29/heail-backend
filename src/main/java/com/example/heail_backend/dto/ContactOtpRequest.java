package com.example.heail_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContactOtpRequest {
    @NotBlank @Email
    String email;

    // Optional — mobile may not be filled in yet when "send code" is clicked,
    // so the SMS leg is best-effort, same reasoning as PartnerOtpRequest.
    String mobile;
}
