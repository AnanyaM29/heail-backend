package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class OrgReportResponse {
    UUID orderId;
    LocalDateTime releasedAt;
    int respondentCount;
    int totalEmployees;
    String overallRag;
    List<PulseRagDto> pulses;
    List<SectionRagDto> sections;
}
