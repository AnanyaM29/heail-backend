package com.example.heail_backend.repository;

import com.example.heail_backend.entity.HrCompetency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrCompetencyRepository extends JpaRepository<HrCompetency, String> {
    List<HrCompetency> findByAssessmentId(Short assessmentId);
}
