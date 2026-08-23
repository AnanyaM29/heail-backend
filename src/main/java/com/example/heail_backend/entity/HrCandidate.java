package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row per candidate per HR order — they take every pillar the buyer
 *  selected on that order, so there's no per-pillar row. Mirrors
 *  OrderEmployee's shape (see that class for the org-employee equivalent),
 *  plus the fields the Company Terms doc requires (DOB, assessment start
 *  date) and the tokenized-link access mechanism candidates use instead of
 *  a normal login — see HrCandidateAccessService. */
@Entity
@Table(name = "hr_candidates")
@Data
public class HrCandidate {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    Order order;

    @Column(nullable = false)
    String name;

    @Column(name = "dob", nullable = false)
    LocalDate dob;

    @Column(nullable = false)
    String email;

    String mobile;

    @Column(name = "assessment_start_date", nullable = false)
    LocalDate assessmentStartDate;

    /** The candidate's throwaway account — created at fulfilment time (order
     *  paid), null before that. Never given a password the candidate knows;
     *  see HrCandidateAccessService.redeem(). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    User user;

    @Column(name = "access_token", unique = true)
    String accessToken;

    @Column(name = "token_expires_at")
    LocalDateTime tokenExpiresAt;

    /** PENDING (drafted, order not yet paid) → SENT (invite emailed) →
     *  ACCESSED (clicked the link) — or EXPIRED / REALLOCATED. This tracks
     *  the invite lifecycle only; actual test progress lives on
     *  AssessmentSession/HrResult via `user`, not duplicated here. */
    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "terms_accepted_at")
    LocalDateTime termsAcceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "PENDING";
    }
}
