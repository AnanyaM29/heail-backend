package com.example.heail_backend.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class OrderEmployeeDto {
    UUID id;
    String name;
    String email;
    String mobile;
    String level;
    String invitationStatus;
}
