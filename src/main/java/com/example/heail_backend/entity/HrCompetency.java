package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/** One of the 46 scored competencies (e.g. "A1-01"). Fixed, seed-only reference
 *  row — never created by the app. assessmentId/skillCategoryId are plain
 *  reference columns, not JPA relations (see HrSkillCategory). minRandomSelection
 *  is how many distinct questions from this competency's pool get drawn into
 *  every attempt of its parent assessment. */
@Entity
@Table(name = "hr_competency")
@Data
public class HrCompetency {

    @Id
    @Column(updatable = false, nullable = false, length = 10)
    String code;

    @Column(name = "assessment_id", nullable = false)
    Short assessmentId;

    @Column(name = "skill_category_id", nullable = false)
    Integer skillCategoryId;

    @Column(nullable = false, columnDefinition = "text")
    String name;

    @Column(name = "min_random_selection", nullable = false)
    short minRandomSelection;
}
