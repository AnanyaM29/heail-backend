package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReallocationRequestDto {
    String newName;
    LocalDate newDob;
    String newEmail;
    String newMobile;
    LocalDate newStartDate;
}
