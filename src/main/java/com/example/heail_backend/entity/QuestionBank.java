package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_bank")
@Data
public class QuestionBank {

    @Id
    @Column(name = "question_id", updatable = false, nullable = false, length = 12)
    String questionId;

    @Column(name = "section_code", nullable = false, length = 3)
    String sectionCode;

    @Column(name = "category_code", nullable = false, length = 3)
    String categoryCode;

    @Column(name = "level_tag", nullable = false, length = 10)
    String levelTag;

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

    // Anchor questions are fixed (same question, every respondent, every wave) and always
    // tagged L+MM+E — they're what makes cross-level gap/divergence/consensus indices
    // computable at all. Rotating (non-anchor) questions still get asked but can't carry a
    // gap figure: different respondents at different levels answer different rotating items.
    @Column(name = "is_anchor", nullable = false, columnDefinition = "boolean default false")
    boolean isAnchor;

    public short scoreFor(char option) {
        return switch (option) {
            case 'A' -> scoreA;
            case 'B' -> scoreB;
            case 'C' -> scoreC;
            case 'D' -> scoreD;
            case 'N' -> 3; // Not applicable / prefer not to answer — scored neutral, never null
            default -> throw new IllegalArgumentException("Invalid option: " + option);
        };
    }
}
