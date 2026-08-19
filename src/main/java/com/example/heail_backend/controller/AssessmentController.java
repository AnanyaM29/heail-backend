package com.example.heail_backend.controller;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leader/assessment")
@RequiredArgsConstructor
// Was hasRole('LEADER') — see OrgOrderController for why. Ownership is enforced
// in AssessmentService via the caller's email on every session/result lookup.
@PreAuthorize("isAuthenticated()")
public class AssessmentController {

    private final AssessmentService assessmentService;

    @PostMapping("/start")
    public ResponseEntity<StartAssessmentResponse> start(Authentication auth) {
        return ResponseEntity.ok(assessmentService.start(auth.getName()));
    }

    @GetMapping("/entitlement")
    public ResponseEntity<EntitlementResponse> entitlement(Authentication auth) {
        EntitlementResponse res = new EntitlementResponse();
        res.setEntitled(assessmentService.hasEntitlement(auth.getName()));
        return ResponseEntity.ok(res);
    }

    @GetMapping("/current")
    public ResponseEntity<SessionResumeResponse> current(Authentication auth) {
        return assessmentService.current(auth.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResumeResponse> resume(@PathVariable UUID sessionId, Authentication auth) {
        return ResponseEntity.ok(assessmentService.resume(sessionId, auth.getName()));
    }

    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<AnswerResponse> answer(@PathVariable UUID sessionId,
                                                  @Valid @RequestBody AnswerRequest req,
                                                  Authentication auth) {
        return ResponseEntity.ok(assessmentService.answer(sessionId, auth.getName(), req));
    }

    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<LeaderResultResponse> submit(@PathVariable UUID sessionId, Authentication auth) {
        return ResponseEntity.ok(assessmentService.submit(sessionId, auth.getName()));
    }

    @GetMapping("/results")
    public ResponseEntity<List<LeaderResultResponse>> results(Authentication auth) {
        return ResponseEntity.ok(assessmentService.listResults(auth.getName()));
    }
}
