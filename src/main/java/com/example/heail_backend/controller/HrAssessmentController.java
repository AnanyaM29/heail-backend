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

    @GetMapping("/assessments")
    public ResponseEntity<List<HrAssessmentDto>> assessments(Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.listAssessments(auth.getName()));
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
    public ResponseEntity<HrResultResponse> submit(@PathVariable UUID sessionId, Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.submit(sessionId, auth.getName()));
    }

    @GetMapping("/results")
    public ResponseEntity<List<HrResultResponse>> results(Authentication auth) {
        return ResponseEntity.ok(hrAssessmentService.listResults(auth.getName()));
    }
}
