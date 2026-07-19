package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class OrgMonitorResponse {
    UUID orderId;
    int totalEmployees;
    int fullyCompletedCount;
    LocalDateTime reportReleasedAt;
    List<EmployeeProgressDto> employees;
}
