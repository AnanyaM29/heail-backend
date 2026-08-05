package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class SectionRagDto {
    String sectionCode;
    String sectionName;
    String pulseCode;

    double index;         // 0-100, all responses (anchor + rotating) in this section
    String band;
    String rag;            // derived display band

    Double levelL;          // 0-100, anchor-only
    Double levelMM;         // 0-100, anchor-only
    Double levelE;          // 0-100, anchor-only
    Double gap;             // L - E, signed, anchor-only; null if suppressed
    String suppressedReason; // non-null only when gap is null

    int sharedQuestionCount; // anchor count for this section (1 or 2 today)
    int respondentCount;
}
