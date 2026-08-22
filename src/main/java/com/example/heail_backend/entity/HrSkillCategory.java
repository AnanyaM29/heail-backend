package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/** A grouping of competencies within one HrAssessment (e.g. "Adaptability & Mindset").
 *  Fixed, seed-only reference row — never created by the app. assessmentId is a
 *  plain reference column, not a JPA relation, matching how QuestionBank.sectionCode
 *  references Section elsewhere in this codebase — reference tables link to each
 *  other by code/id, not by managed association. */
@Entity
@Table(name = "hr_skill_category")
@Data
public class HrSkillCategory {

    @Id
    @Column(updatable = false, nullable = false)
    Integer id;

    @Column(name = "assessment_id", nullable = false)
    Short assessmentId;

    @Column(nullable = false, columnDefinition = "text")
    String name;
}
