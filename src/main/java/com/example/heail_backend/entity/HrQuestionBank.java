package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/** One of the ~5,464 HR competency-assessment questions, 5 options (A-E) with
 *  scores fixed at 5/3/2/1/0 across every row in the source data — unlike
 *  leader_question_bank/question_bank, the best-scoring letter never varies
 *  per question, which is exactly why per-session OptionOrder shuffling
 *  matters here (otherwise "always pick the first option" maxes the score). */
@Entity
@Table(name = "hr_question_bank")
@Data
public class HrQuestionBank {

    @Id
    @Column(name = "question_id", updatable = false, nullable = false, length = 40)
    String questionId;

    @Column(name = "competency_code", nullable = false, length = 10)
    String competencyCode;

    @Column(name = "question_type", nullable = false, length = 10)
    String questionType;

    @Column(nullable = false, columnDefinition = "text")
    String text;

    @Column(name = "option_a", nullable = false, columnDefinition = "text")
    String optionA;

    @Column(name = "option_b", nullable = false, columnDefinition = "text")
    String optionB;

    @Column(name = "option_c", nullable = false, columnDefinition = "text")
    String optionC;

    @Column(name = "option_d", nullable = false, columnDefinition = "text")
    String optionD;

    @Column(name = "option_e", nullable = false, columnDefinition = "text")
    String optionE;

    @Column(name = "score_a", nullable = false)
    short scoreA;

    @Column(name = "score_b", nullable = false)
    short scoreB;

    @Column(name = "score_c", nullable = false)
    short scoreC;

    @Column(name = "score_d", nullable = false)
    short scoreD;

    @Column(name = "score_e", nullable = false)
    short scoreE;

    @Column(nullable = false, columnDefinition = "boolean default true")
    boolean active;

    public short scoreFor(char option) {
        return switch (option) {
            case 'A' -> scoreA;
            case 'B' -> scoreB;
            case 'C' -> scoreC;
            case 'D' -> scoreD;
            case 'E' -> scoreE;
            default -> throw new IllegalArgumentException("Invalid option: " + option);
        };
    }
}
