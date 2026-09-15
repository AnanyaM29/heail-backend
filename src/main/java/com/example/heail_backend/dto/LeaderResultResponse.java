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
    /** True when the attempt was closed out by the 30-minute deadline rather than
     *  finished. When set, the taker sees only the status "Assessment Timed Out" —
     *  no score, no band, no breakdown. */
    boolean timedOut;
    String band;
    Map<String, Integer> domainScores;
    /** Null for results scored before this field existed — the frontend falls back
     *  to 50 per domain in that case. */
    Map<String, Integer> domainMax;
    String strongestPrinciple;
    String strongestPrincipleText;
    String weakestPrinciple;
    String weakestPrincipleText;
    LocalDateTime createdAt;
}
