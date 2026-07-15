package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class QuestionDto {
    String questionId;
    String text;
    String optionA;
    String optionB;
    String optionC;
    String optionD;
}
