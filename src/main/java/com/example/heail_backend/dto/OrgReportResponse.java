package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class OrgReportResponse {
    UUID orderId;
    String organisationName;
    LocalDateTime releasedAt;

    int respondentCount;
    int totalEmployees;
    int respondentsL;
    int respondentsMM;
    int respondentsE;

    double overallIndex;   // 0-100, primary value
    String overallBand;    // emerging / developing / established / leading
    String overallRag;     // derived display band — not primary, kept for anything still reading it

    Double divergenceIndex; // mean(|gap|) across anchor questions, org-wide; null if suppressed

    DataQualityDto dataQuality;
    RiskIndicesDto risk;

    List<PulseRagDto> pulses;
    List<SectionRagDto> sections;   // sorted by |gap| desc, suppressed sections last
    List<QuestionFindingDto> findings; // top questions by |gap|, richest single-question evidence
}
