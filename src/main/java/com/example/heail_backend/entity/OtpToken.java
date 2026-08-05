package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "otp_tokens")
@Data
public class OtpToken {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @Column(nullable = false)
    String email;

    @Column(nullable = false, length = 6)
    String otp;

    // Null for the pre-existing auth flows (register/forgot-password) that never set it —
    // only the newer profile-update flow scopes codes by purpose, so two codes emailed to
    // the same address for two different reasons (e.g. "confirm it's you" vs "confirm your
    // new mobile number") don't invalidate each other.
    String purpose;

    @Column(name = "expires_at", nullable = false)
    LocalDateTime expiresAt;

    boolean used;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
