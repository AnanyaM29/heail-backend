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

    String industry;

    @Column(name = "size_category")
    String sizeCategory;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
