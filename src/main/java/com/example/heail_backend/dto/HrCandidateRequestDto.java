package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Admin-console view of one buyer-submitted reallocation/retake request. */
@Data
public class HrCandidateRequestDto {
    UUID id;
    String type;
    String status;
    UUID orderId;
    String buyerName;
    String buyerEmail;
    UUID candidateId;
    String candidateName;
    String candidateEmail;
    String newName;
    LocalDate newDob;
    String newEmail;
    String newMobile;
    LocalDate newStartDate;
    LocalDateTime createdAt;
}
