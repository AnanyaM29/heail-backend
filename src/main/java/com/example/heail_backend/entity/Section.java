package com.example.heail_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sections")
@Data
public class Section {

    @Id
    @Column(nullable = false, updatable = false, length = 3)
    String code;

    @Column(nullable = false)
    String name;

    @Column(name = "pulse_code")
    String pulseCode;
}
