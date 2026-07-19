package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PulseInfo {
    String pulseCode;
    String state;
    UUID sessionId;
    LocalDateTime completedAt;
}
