package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @Column(nullable = false)
    String name;

    @Column(nullable = false, unique = true)
    String email;

    @Column(name = "password_hash", nullable = false)
    String passwordHash;

    @Column(nullable = false)
    String role;

    @Column(name = "respondent_level")
    String respondentLevel;

    String city;

    String country;

    String mobile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    Organisation organisation;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @Column(name = "last_login_at")
    LocalDateTime lastLoginAt;

    /** True from the moment this account signs in until it explicitly logs out
     *  (or a submitted assessment clears it — see submit() in AssessmentService/
     *  HrAssessmentService/PulseAssessmentService). A second sign-in attempt
     *  while this is true is refused outright — no cooldown, no waiting it out —
     *  see AuthService.login(). Assessment-integrity control: this account must
     *  never be usable from two places at once. */
    @Column(name = "session_active", nullable = false, columnDefinition = "boolean default false")
    boolean sessionActive;

    @Column(nullable = false, columnDefinition = "boolean not null default true")
    boolean active = true;

    @Column(name = "blacklisted_at")
    LocalDateTime blacklistedAt;

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    // Admin-granted, permanent — unlike a coupon (single order, one use, then dead), this
    // discounts every future order this account creates by this percentage (0-100, 0 =
    // no discount, 100 = fully waived) until an admin changes it back. Has a DB default
    // so ddl-auto=update can add this NOT NULL column to a table that already has rows.
    // See getOrCreateDraftOrder/repriceFor* in OrderService/OrgOrderService/HrOrderService.
    @Column(name = "fee_discount_percent", columnDefinition = "integer not null default 0")
    int feeDiscountPercent = 0;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
