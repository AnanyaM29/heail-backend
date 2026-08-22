package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class HrAssessmentDto {
    short id;
    String code;
    String name;
    short questionCount;
    short timeMinutes;
    boolean entitled;
}
