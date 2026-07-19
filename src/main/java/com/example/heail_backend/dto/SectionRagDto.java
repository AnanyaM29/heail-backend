package com.example.heail_backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SectionRagDto {
    String sectionCode;
    String sectionName;
    String rag;
    BigDecimal avgScore;
    int respondentCount;
}
