package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class HrQuestionDto {
    String questionId;
    String text;
    String optionA;
    String optionB;
    String optionC;
    String optionD;
    String optionE;
}
