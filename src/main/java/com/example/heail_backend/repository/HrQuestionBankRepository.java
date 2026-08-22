package com.example.heail_backend.repository;

import com.example.heail_backend.entity.HrQuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrQuestionBankRepository extends JpaRepository<HrQuestionBank, String> {
    List<HrQuestionBank> findByCompetencyCodeAndActiveTrue(String competencyCode);
    List<HrQuestionBank> findByCompetencyCodeInAndActiveTrue(List<String> competencyCodes);
    List<HrQuestionBank> findByQuestionIdIn(List<String> questionIds);
}
