package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** The scored outcome of one completed HR competency-assessment attempt.
 *  Mirrors LeaderResult, generalized for 7 assessments and competency/skill-
 *  category rollups instead of a single domain breakdown. No band — this
 *  product ships as raw competency-gap diagnostics, not a leveled outcome. */
@Entity
@Table(name = "hr_result")
@Data
public class HrResult {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    HrAssessment assessment;

    @Column(name = "attempt_number", nullable = false)
    int attemptNumber;

    @Column(name = "overall_score", nullable = false)
    short overallScore;

    /** Sum of scores per competency actually drawn this attempt — a competency
     *  not sampled is absent from the map, not zero (sampling is random and
     *  uneven per attempt, so absence isn't the same as "scored zero"). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "competency_scores", nullable = false, columnDefinition = "jsonb")
    Map<String, Integer> competencyScores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_category_scores", nullable = false, columnDefinition = "jsonb")
    Map<String, Integer> skillCategoryScores;

    @Column(name = "strongest_competency", length = 10)
    String strongestCompetency;

    @Column(name = "weakest_competency", length = 10)
    String weakestCompetency;

    /** True when this attempt was closed by the 30-minute deadline with questions
     *  still unanswered. overallScore is then marks achieved out of the full paper
     *  (unanswered questions count as zero). */
    @Column(name = "timed_out", nullable = false, columnDefinition = "boolean default false")
    boolean timedOut;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
