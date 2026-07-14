package com.heail.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organisations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Organisation {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "industry", length = 255)
    private String industry;

    @Column(name = "size_category", length = 100)
    private String sizeCategory;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
