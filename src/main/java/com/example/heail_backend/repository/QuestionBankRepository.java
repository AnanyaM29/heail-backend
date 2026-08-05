package com.example.heail_backend.repository;

import com.example.heail_backend.entity.QuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, String> {
    List<QuestionBank> findBySectionCodeAndCategoryCodeAndActiveTrue(String sectionCode, String categoryCode);
    List<QuestionBank> findBySectionCodeAndActiveTrue(String sectionCode);
    List<QuestionBank> findByQuestionIdIn(List<String> questionIds);
    List<QuestionBank> findBySectionCodeAndIsAnchorTrueAndActiveTrue(String sectionCode);
    List<QuestionBank> findByIsAnchorTrueAndActiveTrue();
}
