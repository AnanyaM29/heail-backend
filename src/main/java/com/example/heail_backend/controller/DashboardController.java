package com.example.heail_backend.controller;

import com.example.heail_backend.dto.DashboardResponse;
import com.example.heail_backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One view of everything the logged-in person is doing across every HEAIL
 *  product, regardless of which single role their account happens to carry. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard(Authentication auth) {
        return ResponseEntity.ok(dashboardService.getDashboard(auth.getName()));
    }
}
