package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class HrCandidateDto {
    UUID id;
    UUID orderId;
    String name;
    LocalDate dob;
    String email;
    String mobile;
    LocalDate assessmentStartDate;
    String status;
    LocalDateTime tokenExpiresAt;
    boolean canRequestRetake;
    List<HrCandidateResultSummary> results;
}
