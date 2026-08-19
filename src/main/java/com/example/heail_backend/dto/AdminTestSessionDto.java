package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AdminTestSessionDto {
    UUID id;
    String userName;
    String userEmail;
    String organisationName;
    String productCode;
    String pulse;
    String status;
    int attemptNumber;
    LocalDateTime startedAt;
    LocalDateTime completedAt;
}
