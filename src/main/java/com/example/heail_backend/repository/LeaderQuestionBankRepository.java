package com.example.heail_backend.repository;

import com.example.heail_backend.entity.LeaderQuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaderQuestionBankRepository extends JpaRepository<LeaderQuestionBank, String> {
    List<LeaderQuestionBank> findByPrincipleCodeAndActiveTrue(String principleCode);
    List<LeaderQuestionBank> findByQuestionIdIn(List<String> questionIds);
}
