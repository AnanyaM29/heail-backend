package com.example.heail_backend.controller;

import com.example.heail_backend.dto.ReallocationRequestDto;
import com.example.heail_backend.service.HrCandidateRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Buyer-facing: submit a reallocation/retake request for review. Approval
 *  happens in AdminDashboardController — see HrCandidateRequestService. */
@RestController
@RequestMapping("/api/v1/hr/candidates")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class HrCandidateRequestController {

    private final HrCandidateRequestService requestService;

    @PostMapping("/{candidateId}/request-reallocation")
    public ResponseEntity<?> requestReallocation(@PathVariable UUID candidateId,
                                                  @RequestBody ReallocationRequestDto body,
                                                  Authentication auth) {
        requestService.requestReallocation(candidateId, auth.getName(), body);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{candidateId}/request-retake")
    public ResponseEntity<?> requestRetake(@PathVariable UUID candidateId, Authentication auth) {
        requestService.requestRetake(candidateId, auth.getName());
        return ResponseEntity.ok().build();
    }
}
