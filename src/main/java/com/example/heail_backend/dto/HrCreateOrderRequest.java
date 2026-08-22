package com.example.heail_backend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class HrCreateOrderRequest {

    @NotEmpty(message = "Select at least one assessment")
    List<Short> assessmentIds;
}
