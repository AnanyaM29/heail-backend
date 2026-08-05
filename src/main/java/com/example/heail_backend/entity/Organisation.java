package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organisations")
@Data
public class Organisation {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @Column(nullable = false)
    String name;

    // Filled at org registration, used to compute the minimum sample size
    // required on the employee-upload step (see OrgOrderService.minRequiredEmployees).
    Integer headcount;

    // Filled later, before payment (buy-org employee-details step) — not
    // collected at registration time.
    String industry;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
