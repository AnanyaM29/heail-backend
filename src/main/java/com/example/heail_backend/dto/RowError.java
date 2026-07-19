package com.example.heail_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 1-based row number, matching how a spreadsheet/CSV row is referred to by a human. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RowError {
    int index;
    String message;
}
