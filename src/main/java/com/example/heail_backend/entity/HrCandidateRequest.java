package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** A buyer-submitted reallocation or retake request, awaiting superadmin
 *  review — both are described in Company Terms HR.docx as requests HEAIL
 *  "vets," not instant self-service. Approving one is handled in
 *  AdminDashboardService (mirrors its existing moderation-action shape). */
@Entity
@Table(name = "hr_candidate_requests")
@Data
public class HrCandidateRequest {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    HrCandidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    Order order;

    /** REALLOCATION or RETAKE. */
    @Column(nullable = false)
    String type;

    /** PENDING → APPROVED / REJECTED. */
    @Column(nullable = false)
    String status;

    // Only set (and only used) for type = REALLOCATION.
    String newName;
    LocalDate newDob;
    String newEmail;
    String newMobile;
    LocalDate newStartDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    User reviewedBy;

    LocalDateTime reviewedAt;
    String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "PENDING";
    }
}
