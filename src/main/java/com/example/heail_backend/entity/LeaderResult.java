package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "leader_results")
@Data
public class LeaderResult {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    AssessmentSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "attempt_number", nullable = false)
    int attemptNumber;

    @Column(name = "overall_score", nullable = false)
    short overallScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    LeaderBand band;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "domain_scores", nullable = false, columnDefinition = "jsonb")
    Map<String, Integer> domainScores;

    @Column(name = "strongest_principle", length = 3)
    String strongestPrinciple;

    @Column(name = "weakest_principle", length = 3)
    String weakestPrinciple;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
