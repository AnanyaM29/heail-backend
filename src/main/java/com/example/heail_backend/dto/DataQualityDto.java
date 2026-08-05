package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class DataQualityDto {
    int respondentsL;
    int respondentsMM;
    int respondentsE;

    int sharedQuestions;         // total anchor count across the instrument
    int straightliningCount;     // respondents flagged for 8+ consecutive identical answers
    double silenceRatePct;       // NA rate, all responses
    double voiceConfidence;      // 1 - (NA rate + straight-lining respondent rate), clamped >= 0
    int suppressedSectionCount;  // sections where the gap figure is suppressed
    Double medianCompletionMinutes; // per-Pulse session duration (started_at -> completed_at); null if no completed sessions
}
