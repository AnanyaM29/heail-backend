package com.example.heail_backend.repository;

import com.example.heail_backend.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {
    List<Answer> findBySessionId(UUID sessionId);
    Optional<Answer> findBySessionIdAndQuestionId(UUID sessionId, String questionId);
    List<Answer> findBySessionIdIn(List<UUID> sessionIds);
}
