package com.example.heail_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AnswerRequest {

    @NotBlank
    String questionId;

    @NotBlank
    @Pattern(regexp = "[A-DN]", message = "selectedOption must be one of A, B, C, D, or N (not applicable)")
    String selectedOption;
}
