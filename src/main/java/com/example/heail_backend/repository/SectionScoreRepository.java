package com.example.heail_backend.repository;

import com.example.heail_backend.entity.AssessmentSession;
import com.example.heail_backend.entity.SectionScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectionScoreRepository extends JpaRepository<SectionScore, java.util.UUID> {
    List<SectionScore> findBySession(AssessmentSession session);
    List<SectionScore> findBySessionIn(List<AssessmentSession> sessions);
}
