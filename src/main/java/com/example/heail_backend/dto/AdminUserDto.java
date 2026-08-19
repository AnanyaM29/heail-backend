package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AdminUserDto {
    UUID id;
    String name;
    String email;
    String role;
    String city;
    String country;
    String organisationName;
    LocalDateTime createdAt;
    LocalDateTime lastLoginAt;
    boolean active;
    LocalDateTime blacklistedAt;
    LocalDateTime deletedAt;
}
