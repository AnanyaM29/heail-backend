package com.example.heail_backend.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class EmployeeProgressDto {
    UUID id;
    String name;
    String email;
    String invitationStatus;
    Map<String, String> pulseStates;
    boolean allCompleted;
}
