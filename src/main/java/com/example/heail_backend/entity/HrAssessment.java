package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/** One of the 7 HR competency pillars — a fixed, seed-only reference row (id 1..7,
 *  matching "Assessment No." in the source taxonomy). Never created by the app. */
@Entity
@Table(name = "hr_assessment")
@Data
public class HrAssessment {

    @Id
    @Column(updatable = false, nullable = false)
    Short id;

    @Column(nullable = false, unique = true, length = 40)
    String code;

    @Column(nullable = false, columnDefinition = "text")
    String name;

    @Column(name = "question_count", nullable = false, columnDefinition = "smallint default 30")
    short questionCount;

    @Column(name = "time_minutes", nullable = false, columnDefinition = "smallint default 30")
    short timeMinutes;
}
