package com.example.heail_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    String name;

    @NotBlank @Email
    String email;

    @NotBlank @Size(min = 8)
    String password;

    @NotBlank
    String role;

    String organisationName;
    String industry;
    String sizeCategory;

    String organisationId;
    String respondentLevel;
}
