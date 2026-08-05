package com.example.heail_backend.controller;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.service.PulseAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employee/pulse")
@RequiredArgsConstructor
// Was hasRole('EMPLOYEE') — see OrgOrderController for why. Ownership is enforced
// in PulseAssessmentService via requireActiveRound(user), keyed off the caller's
// email, not their stored role.
@PreAuthorize("isAuthenticated()")
public class PulseAssessmentController {

    private final PulseAssessmentService pulseService;

    @GetMapping("/status")
    public ResponseEntity<PulseStatusResponse> status(Authentication auth) {
        return ResponseEntity.ok(pulseService.status(auth.getName()));
    }

    @PostMapping("/{pulseCode}/start")
    public ResponseEntity<StartAssessmentResponse> start(@PathVariable String pulseCode, Authentication auth) {
        return ResponseEntity.ok(pulseService.start(pulseCode, auth.getName()));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResumeResponse> resume(@PathVariable UUID sessionId, Authentication auth) {
        return ResponseEntity.ok(pulseService.resume(sessionId, auth.getName()));
    }

    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<AnswerResponse> answer(@PathVariable UUID sessionId,
                                                  @Valid @RequestBody AnswerRequest req,
                                                  Authentication auth) {
        return ResponseEntity.ok(pulseService.answer(sessionId, auth.getName(), req));
    }

    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<PulseSubmitResponse> submit(@PathVariable UUID sessionId, Authentication auth) {
        return ResponseEntity.ok(pulseService.submit(sessionId, auth.getName()));
    }
}
