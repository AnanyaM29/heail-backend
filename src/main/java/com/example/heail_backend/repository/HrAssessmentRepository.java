package com.example.heail_backend.repository;

import com.example.heail_backend.entity.HrAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrAssessmentRepository extends JpaRepository<HrAssessment, Short> {
    List<HrAssessment> findAllByOrderByIdAsc();
}
