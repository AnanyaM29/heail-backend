package com.example.heail_backend.controller;

import com.example.heail_backend.dto.AuthResponse;
import com.example.heail_backend.dto.HrCandidateTokenInfoResponse;
import com.example.heail_backend.service.HrCandidateAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public — no login. See SecurityConfig's permitAll list and
 *  HrCandidateAccessService's class doc for why this is safe: the access
 *  token itself is the only credential, and redeem() hands back a normal
 *  scoped JWT the rest of the app already knows how to check. */
@RestController
@RequestMapping("/api/v1/hr/candidate")
@RequiredArgsConstructor
public class HrCandidateAccessController {

    private final HrCandidateAccessService accessService;

    @GetMapping("/{token}")
    public ResponseEntity<HrCandidateTokenInfoResponse> tokenInfo(@PathVariable String token) {
        return ResponseEntity.ok(accessService.tokenInfo(token));
    }

    @PostMapping("/{token}/accept-terms")
    public ResponseEntity<?> acceptTerms(@PathVariable String token) {
        accessService.acceptTerms(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{token}/redeem")
    public ResponseEntity<AuthResponse> redeem(@PathVariable String token) {
        return ResponseEntity.ok(accessService.redeem(token));
    }
}
