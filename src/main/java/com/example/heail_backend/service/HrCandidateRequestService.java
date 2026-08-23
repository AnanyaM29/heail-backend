package com.example.heail_backend.service;

import com.example.heail_backend.dto.ReallocationRequestDto;
import com.example.heail_backend.entity.HrCandidate;
import com.example.heail_backend.entity.HrCandidateRequest;
import com.example.heail_backend.repository.HrCandidateRepository;
import com.example.heail_backend.repository.HrCandidateRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Buyer-facing half of the reallocation/retake flow — creating a PENDING
 * request. The admin-facing approve/reject half (which does the actual
 * reallocation/retake work) lives in AdminDashboardService, next to the
 * rest of the superadmin moderation actions it's one of.
 */
@Service
@RequiredArgsConstructor
public class HrCandidateRequestService {

    private final HrCandidateRepository hrCandidateRepo;
    private final HrCandidateRequestRepository requestRepo;

    @Transactional
    public void requestReallocation(UUID candidateId, String email, ReallocationRequestDto body) {
        HrCandidate candidate = requireOwnedCandidate(candidateId, email);
        requireNoPendingRequest(candidateId);

        if (!"SENT".equals(candidate.getStatus()))
            throw new IllegalArgumentException("Only an unstarted candidate can be reallocated");
        if (candidate.getOrder().getPaidAt() == null
                || candidate.getOrder().getPaidAt().plusDays(7).isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Reallocation requests must be made within 7 days of payment");
        if (body.getNewName() == null || body.getNewName().isBlank()
                || body.getNewEmail() == null || body.getNewEmail().isBlank()
                || body.getNewDob() == null || body.getNewStartDate() == null)
            throw new IllegalArgumentException("New candidate's name, DOB, email and start date are required");

        HrCandidateRequest req = new HrCandidateRequest();
        req.setCandidate(candidate);
        req.setOrder(candidate.getOrder());
        req.setType("REALLOCATION");
        req.setNewName(body.getNewName().trim());
        req.setNewDob(body.getNewDob());
        req.setNewEmail(body.getNewEmail().trim().toLowerCase());
        req.setNewMobile(body.getNewMobile());
        req.setNewStartDate(body.getNewStartDate());
        requestRepo.save(req);
    }

    @Transactional
    public void requestRetake(UUID candidateId, String email) {
        HrCandidate candidate = requireOwnedCandidate(candidateId, email);
        requireNoPendingRequest(candidateId);

        if (candidate.getUser() == null || "PENDING".equals(candidate.getStatus()))
            throw new IllegalArgumentException("This candidate hasn't been sent an invitation yet");
        if ("REALLOCATED".equals(candidate.getStatus()))
            throw new IllegalArgumentException("This candidate's credit was reallocated to someone else");

        HrCandidateRequest req = new HrCandidateRequest();
        req.setCandidate(candidate);
        req.setOrder(candidate.getOrder());
        req.setType("RETAKE");
        requestRepo.save(req);
    }

    private void requireNoPendingRequest(UUID candidateId) {
        if (!requestRepo.findByCandidateIdAndStatus(candidateId, "PENDING").isEmpty())
            throw new IllegalArgumentException("A request for this candidate is already pending review");
    }

    private HrCandidate requireOwnedCandidate(UUID candidateId, String email) {
        HrCandidate candidate = hrCandidateRepo.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));
        if (!candidate.getOrder().getUser().getEmail().equalsIgnoreCase(email))
            throw new IllegalArgumentException("Candidate not found");
        return candidate;
    }
}
