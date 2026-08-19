package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class StartAssessmentResponse {
    UUID sessionId;
    int attemptNumber;
    List<QuestionDto> questions;
    LocalDateTime deadlineAt;
}
