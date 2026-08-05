package com.example.heail_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank
    String name;

    String city;
    String country;

    @NotBlank
    String email;

    String mobile;
}
