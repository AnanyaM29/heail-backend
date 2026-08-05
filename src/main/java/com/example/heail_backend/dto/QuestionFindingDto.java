package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class QuestionFindingDto {
    String questionId;
    String sectionCode;
    String sectionName;
    String pulseCode;
    String text;

    Double levelL;
    Double levelMM;
    Double levelE;
    double gap; // signed L - E

    int nL;
    int nMM;
    int nE;
}
