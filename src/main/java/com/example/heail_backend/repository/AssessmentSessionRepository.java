package com.example.heail_backend.repository;

import com.example.heail_backend.entity.AssessmentSession;
import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.SessionStatus;
import com.example.heail_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, UUID> {
    List<AssessmentSession> findByUserAndProductCodeOrderByAttemptNumberDesc(User user, String productCode);
    Optional<AssessmentSession> findFirstByUserAndProductCodeAndStatusOrderByStartedAtDesc(
            User user, String productCode, SessionStatus status);
    List<AssessmentSession> findByUserAndOrderAndPulse(User user, Order order, String pulse);
    List<AssessmentSession> findByUserAndStatusAndProductCodeStartingWith(User user, SessionStatus status, String productCodePrefix);
    List<AssessmentSession> findByOrderAndStatus(Order order, SessionStatus status);

    /**
     * Paged + searchable feed for the admin Tests tab. Both joins are FETCH
     * joins, not plain ones — toTestDto() reads session.getUser() and
     * user.getOrganisation() for every row, and a plain join only filters,
     * it doesn't hydrate those associations (see UserRepository.searchActive
     * for the full N+1 explanation).
     */
    @Query("SELECT s FROM AssessmentSession s JOIN FETCH s.user u LEFT JOIN FETCH u.organisation o WHERE s.startedAt > :cutoff AND (" +
           ":q = '' OR " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(o.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.productCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.pulse) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<AssessmentSession> search(@Param("cutoff") LocalDateTime cutoff, @Param("q") String q, Pageable pageable);
}
