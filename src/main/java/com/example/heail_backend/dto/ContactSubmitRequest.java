package com.example.heail_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContactSubmitRequest {
    @NotBlank
    String name;

    @NotBlank
    String mobile;

    @NotBlank @Email
    String email;

    @NotBlank
    String city;

    @NotBlank
    String country;

    @NotBlank
    String message;

    @NotBlank
    String otp;
}
