package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assessment_session")
@Data
public class AssessmentSession {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "product_code", nullable = false)
    String productCode;

    /** Null for leader sessions; one of LEADER_PULSE/TALENT_PULSE/SYSTEM_PULSE/GROWTH_PULSE for org sessions. */
    @Column(name = "pulse")
    String pulse;

    /** Null for leader sessions; the org round (Order) this employee session belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    Order order;

    @Column(name = "attempt_number", nullable = false)
    int attemptNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_ids", nullable = false, columnDefinition = "jsonb")
    List<String> questionIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SessionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    LocalDateTime startedAt;

    @Column(name = "completed_at")
    LocalDateTime completedAt;

    @PrePersist
    void prePersist() {
        this.startedAt = LocalDateTime.now();
    }
}
