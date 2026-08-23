package com.example.heail_backend.repository;

import com.example.heail_backend.entity.HrCandidateRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HrCandidateRequestRepository extends JpaRepository<HrCandidateRequest, UUID> {
    List<HrCandidateRequest> findByStatusOrderByCreatedAtAsc(String status);
    List<HrCandidateRequest> findByCandidateIdAndStatus(UUID candidateId, String status);
}
