package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Data
public class RefreshToken {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @Column(nullable = false, unique = true)
    String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "expires_at", nullable = false)
    LocalDateTime expiresAt;

    @Column(nullable = false)
    boolean revoked;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
