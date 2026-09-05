package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A test-taker's view of one HR attempt. Score fields are intentionally left
 * null when this is served to the person who took the test — HR results are
 * shown only to the buyer (see HrAssessmentService.toResponse). They remain on
 * the DTO for any buyer-facing use and to keep the shape stable for the client.
 */
@Data
public class HrResultResponse {
    UUID id;
    UUID sessionId;
    short assessmentId;
    String assessmentCode;
    String assessmentName;
    int attemptNumber;
    Integer overallScore;
    Map<String, Integer> competencyScores;
    Map<String, Integer> skillCategoryScores;
    String strongestCompetency;
    String strongestCompetencyName;
    String weakestCompetency;
    String weakestCompetencyName;
    LocalDateTime createdAt;
}
