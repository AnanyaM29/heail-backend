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

    @Column(name = "question_id", nullable = false, length = 12)
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
