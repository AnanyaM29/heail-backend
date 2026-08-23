package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CandidateRowRequest {
    String name;
    LocalDate dob;
    String email;
    String mobile;
    LocalDate assessmentStartDate;
}
