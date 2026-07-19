package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "org_section_results")
@Data
public class OrgSectionResult {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    Order order;

    @Column(name = "section_code", nullable = false, length = 3)
    String sectionCode;

    @Column(name = "respondent_count", nullable = false)
    int respondentCount;

    @Column(name = "avg_score")
    BigDecimal avgScore;

    @Column(length = 20)
    String rag;

    @Column(name = "computed_at", nullable = false, updatable = false)
    LocalDateTime computedAt;

    @PrePersist
    void prePersist() {
        this.computedAt = LocalDateTime.now();
    }
}
