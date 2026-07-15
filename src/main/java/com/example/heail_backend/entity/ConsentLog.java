package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consent_log")
@Data
public class ConsentLog {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "agreement_type", nullable = false)
    String agreementType;

    @Column(nullable = false)
    String version;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    LocalDateTime acceptedAt;

    @Column(name = "ip_address")
    String ipAddress;

    @PrePersist
    void prePersist() {
        this.acceptedAt = LocalDateTime.now();
    }
}
