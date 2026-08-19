package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AdminPartnerDto {
    UUID id;
    String name;
    String country;
    String city;
    String mobile;
    String email;
    boolean consentGiven;
    String resumeFileName;
    boolean hasResume;
    LocalDateTime createdAt;
}
