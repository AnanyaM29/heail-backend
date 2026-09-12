package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * The human-readable name of each Gita Leader principle, keyed by its code
 * ("P01".."P50"). Populated once from db/seed/leader_principle_seed.sql — the
 * question bank only carries the code, not a label, so this is the single place
 * a principle's name lives. Surfaced on the result as strongest/weakest
 * principle text (see AssessmentService.toResponse).
 */
@Entity
@Table(name = "leader_principle")
@Data
public class LeaderPrinciple {

    @Id
    @Column(length = 3)
    String code;

    @Column(nullable = false, columnDefinition = "text")
    String name;
}
