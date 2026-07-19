package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class EmployeeRowRequest {
    String name;
    String email;
    String mobile;
    String level;
}
