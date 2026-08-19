package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class SessionResumeResponse {
    UUID sessionId;
    int attemptNumber;
    String status;
    List<QuestionDto> questions;
    Map<String, String> answeredOptions;
    LocalDateTime deadlineAt;
}
