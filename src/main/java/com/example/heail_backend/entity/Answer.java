package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "answers")
@Data
public class Answer {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    AssessmentSession session;

    // Widened from 12 to 40 for HR competency-bank item IDs (e.g.
    // "ITEM-MCQ-0TO2-REF-011", up to 22 chars) — matches hr_question_bank.question_id.
    @Column(name = "question_id", nullable = false, length = 40)
    String questionId;

    @Column(name = "selected_option", nullable = false, length = 1)
    char selectedOption;

    @Column(nullable = false)
    short score;

    @Column(name = "answered_at", nullable = false)
    LocalDateTime answeredAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.answeredAt = LocalDateTime.now();
    }
}
