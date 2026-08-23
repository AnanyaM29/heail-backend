package com.example.heail_backend.repository;

import com.example.heail_backend.entity.HrCandidate;
import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrCandidateRepository extends JpaRepository<HrCandidate, UUID> {
    List<HrCandidate> findByOrder(Order order);
    void deleteByOrder(Order order);
    Optional<HrCandidate> findByAccessToken(String accessToken);
    List<HrCandidate> findByUser(User user);
    List<HrCandidate> findByOrderUserOrderByCreatedAtDesc(User buyer);
    List<HrCandidate> findByStatusInAndTokenExpiresAtBefore(List<String> statuses, LocalDateTime cutoff);
}
