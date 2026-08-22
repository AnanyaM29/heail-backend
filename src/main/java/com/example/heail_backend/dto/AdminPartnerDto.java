package com.example.heail_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
