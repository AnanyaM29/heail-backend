package com.example.heail_backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PulseRagDto {
    String pulseCode;
    String displayName;
    String rag;
    BigDecimal avgScore;
}
