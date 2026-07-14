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

    @Column(name = "expires_at", nullable = false)
    LocalDateTime expiresAt;

    boolean used;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
