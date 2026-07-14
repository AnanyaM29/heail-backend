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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    Organisation organisation;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
