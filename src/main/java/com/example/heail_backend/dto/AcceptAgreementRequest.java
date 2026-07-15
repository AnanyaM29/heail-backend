package com.example.heail_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcceptAgreementRequest {

    @NotBlank
    String version;
}
