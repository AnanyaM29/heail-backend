package com.example.heail_backend.service;

import com.example.heail_backend.dto.AuthResponse;
import com.example.heail_backend.dto.HrCandidateTokenInfoResponse;
import com.example.heail_backend.entity.Entitlement;
import com.example.heail_backend.entity.HrAssessment;
import com.example.heail_backend.entity.HrCandidate;
import com.example.heail_backend.repository.EntitlementRepository;
import com.example.heail_backend.repository.HrAssessmentRepository;
import com.example.heail_backend.repository.HrCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public, token-scoped access for HR candidates — no HEAIL account/password
 * involved from the candidate's side. A candidate row's `user` (created at
 * HrOrderService.fulfilCandidates() time) is a real account under the hood,
 * purely so AssessmentSession/HrResult/Entitlement — all non-nullable User
 * FKs — work unmodified; redeem() just mints that user a normal JWT via
 * AuthService.buildAuthResponse(), the same call a real login makes. Once
 * redeemed, the candidate is indistinguishable from any other logged-in user
 * to the rest of the app (HrPlayerComponent, HrAssessmentController, etc.
 * are untouched).
 */
@Service
@RequiredArgsConstructor
public class HrCandidateAccessService {

    private final HrCandidateRepository hrCandidateRepo;
    private final HrAssessmentRepository hrAssessmentRepo;
    private final EntitlementRepository entitlementRepo;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public HrCandidateTokenInfoResponse tokenInfo(String token) {
        HrCandidate candidate = requireValidToken(token);

        HrCandidateTokenInfoResponse res = new HrCandidateTokenInfoResponse();
        res.setCandidateName(candidate.getName());
        res.setAssessmentNames(assessmentNamesFor(candidate));
        res.setTermsAccepted(candidate.getTermsAcceptedAt() != null);
        return res;
    }

    @Transactional
    public void acceptTerms(String token) {
        HrCandidate candidate = requireValidToken(token);
        if (candidate.getTermsAcceptedAt() == null) {
            candidate.setTermsAcceptedAt(LocalDateTime.now());
            hrCandidateRepo.save(candidate);
        }
    }

    @Transactional
    public AuthResponse redeem(String token) {
        HrCandidate candidate = requireValidToken(token);
        if (candidate.getTermsAcceptedAt() == null)
            throw new IllegalArgumentException("Accept the Candidate Terms before starting");
        if (candidate.getUser() == null)
            throw new IllegalStateException("This candidate's account isn't ready yet — try again shortly");

        // Single-use once every assigned pillar has been started: the assessment is
        // meant to be accessible only once, and only to the person the buyer
        // registered. A resume of an in-progress pillar uses the JWT already held
        // in that person's browser, not a fresh redeem of the link.
        boolean somethingLeftToStart = entitlementRepo.findByUser(candidate.getUser()).stream()
                .anyMatch(e -> e.getProductCode().startsWith("HR_A") && !e.isUsed());
        if ("ACCESSED".equals(candidate.getStatus()) && !somethingLeftToStart)
            throw new AccessDeniedException(
                    "This assessment link has already been used. If you were interrupted mid-assessment, "
                    + "contact whoever invited you to arrange a new link.");

        if (!"ACCESSED".equals(candidate.getStatus())) {
            candidate.setStatus("ACCESSED");
            hrCandidateRepo.save(candidate);
        }

        // Records this as the account's current session (no cooldown check here —
        // the candidate must always be able to reach their own test) so that a
        // SEPARATE login attempt on this account shortly after (e.g. someone using
        // the candidate's password on a second machine) is refused by
        // AuthService.login()'s single-session guard.
        authService.recordSessionStart(candidate.getUser());

        return authService.buildAuthResponse(candidate.getUser());
    }

    private List<String> assessmentNamesFor(HrCandidate candidate) {
        if (candidate.getUser() == null) return List.of();
        return entitlementRepo.findByUser(candidate.getUser()).stream()
                .map(Entitlement::getProductCode)
                .filter(code -> code.startsWith("HR_A"))
                .distinct()
                .map(code -> Short.parseShort(code.substring("HR_A".length())))
                .map(id -> hrAssessmentRepo.findById(id).map(HrAssessment::getName).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private HrCandidate requireValidToken(String token) {
        HrCandidate candidate = hrCandidateRepo.findByAccessToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or unknown access link"));
        if ("REALLOCATED".equals(candidate.getStatus()))
            throw new AccessDeniedException("This access link is no longer valid");
        if (candidate.getTokenExpiresAt() == null || candidate.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            if (!"EXPIRED".equals(candidate.getStatus())) {
                candidate.setStatus("EXPIRED");
                hrCandidateRepo.save(candidate);
            }
            throw new AccessDeniedException("This access link has expired");
        }
        return candidate;
    }
}
