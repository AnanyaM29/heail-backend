package com.example.heail_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetOrgDetailsRequest {
    @NotBlank
    String organisationName;

    @NotNull
    Integer headcount;

    String industry;
}
