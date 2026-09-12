package com.example.heail_backend.controller;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.service.HrAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
// Ownership enforced in HrAssessmentService via the caller's email on every
// session/result lookup — same pattern as AssessmentController/PulseAssessmentController.
@PreAuthorize("isAuthenticated()")
public class HrAssessmentController {

    private final HrAssessmentService hrAssessmentService;

    // Public — browsing the 7 pillars (prices/names/question counts) needs no
    // account; only actually starting a purchase does. auth is null for an
    // anonymous caller, in which case every pillar just comes back
    // not-entitled (see HrAssessmentService.listAssessments).
    @PreAuthorize("permitAll()")
    @GetMapping("/assessments")
    public ResponseEntity<List<HrAssessmentDto>> assessments(Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.listAssessments(auth != null ? auth.getName() : null));
    }

    // Every individual assignment this person holds — one card per assignment,
    // even when the same pillar was assigned to them more than once. Distinct
    // from /assessments above, which is the 7-pillar catalogue (one row per
    // pillar type) used for browsing/buying.
    @GetMapping("/assessments/mine")
    public ResponseEntity<List<HrAssignmentDto>> myAssignments(Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.listAssignments(auth.getName()));
    }

    @PostMapping("/assessments/{assessmentId}/start")
    public ResponseEntity<HrStartAssessmentResponse> start(@PathVariable short assessmentId, Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.start(assessmentId, auth.getName()));
    }

    @GetMapping("/assessments/{assessmentId}/current")
    public ResponseEntity<HrSessionResumeResponse> current(@PathVariable short assessmentId, Authentication auth) {
        return hrAssessmentService.current(assessmentId, auth.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<HrSessionResumeResponse> resume(@PathVariable UUID sessionId, Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.resume(sessionId, auth.getName()));
    }

    @PostMapping("/sessions/{sessionId}/answer")
    public ResponseEntity<AnswerResponse> answer(@PathVariable UUID sessionId,
                                                  @Valid @RequestBody HrAnswerRequest req,
                                                  Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.answer(sessionId, auth.getName(), req));
    }

    @PostMapping("/sessions/{sessionId}/submit")
    public ResponseEntity<HrResultResponse> submit(@PathVariable UUID sessionId,
                                                    @RequestParam(defaultValue = "false") boolean forced,
                                                    Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.submit(sessionId, auth.getName()));
    }

    @GetMapping("/results")
    public ResponseEntity<List<HrResultResponse>> results(Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.listResults(auth.getName()));
    }
}
