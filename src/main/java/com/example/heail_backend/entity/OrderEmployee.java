package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_employees")
@Data
public class OrderEmployee {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    Order order;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    String email;

    String mobile;

    @Column(nullable = false, length = 4)
    String level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    User user;

    @Column(name = "invitation_status", nullable = false)
    String invitationStatus;

    @Column(name = "last_reminder_at")
    LocalDateTime lastReminderAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.invitationStatus == null) this.invitationStatus = "PENDING";
    }
}
