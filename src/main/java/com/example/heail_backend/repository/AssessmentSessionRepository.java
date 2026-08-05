package com.example.heail_backend.repository;

import com.example.heail_backend.entity.AssessmentSession;
import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.SessionStatus;
import com.example.heail_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, UUID> {
    List<AssessmentSession> findByUserAndProductCodeOrderByAttemptNumberDesc(User user, String productCode);
    Optional<AssessmentSession> findFirstByUserAndProductCodeAndStatusOrderByStartedAtDesc(
            User user, String productCode, SessionStatus status);
    List<AssessmentSession> findByUserAndOrderAndPulse(User user, Order order, String pulse);
    List<AssessmentSession> findByOrderAndStatus(Order order, SessionStatus status);
}
