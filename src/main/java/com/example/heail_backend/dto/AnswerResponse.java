package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class AnswerResponse {
    String questionId;
    String selectedOption;
    int answeredCount;
    int totalQuestions;
}
