package com.heail.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "\"users\"")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    private Organisation organisation;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // Values: ADMIN, ORG_ADMIN, RESPONDENT
    @Column(name = "role", nullable = false, length = 50)
    private String role;

    // Values: L, MM, E  (null for ADMIN / ORG_ADMIN)
    @Column(name = "respondent_level", length = 10)
    private String respondentLevel;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
