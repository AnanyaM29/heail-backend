package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "leader_question_bank")
@Data
public class LeaderQuestionBank {

    @Id
    @Column(name = "question_id", updatable = false, nullable = false, length = 10)
    String questionId;

    @Column(name = "principle_code", nullable = false, length = 3)
    String principleCode;

    @Column(nullable = false, length = 3)
    String domain;

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

    @Column(name = "score_a", nullable = false)
    short scoreA;

    @Column(name = "score_b", nullable = false)
    short scoreB;

    @Column(name = "score_c", nullable = false)
    short scoreC;

    @Column(name = "score_d", nullable = false)
    short scoreD;

    @Column(nullable = false)
    boolean active;

    public short scoreFor(char option) {
        return switch (option) {
            case 'A' -> scoreA;
            case 'B' -> scoreB;
            case 'C' -> scoreC;
            case 'D' -> scoreD;
            default -> throw new IllegalArgumentException("Invalid option: " + option);
        };
    }
}
