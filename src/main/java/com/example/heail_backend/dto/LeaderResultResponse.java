package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class LeaderResultResponse {
    UUID id;
    UUID sessionId;
    int attemptNumber;
    int overallScore;
    String band;
    Map<String, Integer> domainScores;
    String strongestPrinciple;
    String weakestPrinciple;
    LocalDateTime createdAt;
}
