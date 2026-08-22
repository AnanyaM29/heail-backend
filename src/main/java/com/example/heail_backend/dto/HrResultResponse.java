package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class HrResultResponse {
    UUID id;
    UUID sessionId;
    short assessmentId;
    String assessmentCode;
    String assessmentName;
    int attemptNumber;
    int overallScore;
    Map<String, Integer> competencyScores;
    Map<String, Integer> skillCategoryScores;
    String strongestCompetency;
    String strongestCompetencyName;
    String weakestCompetency;
    String weakestCompetencyName;
    LocalDateTime createdAt;
}
