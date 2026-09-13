package com.example.heail_backend.controller;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/send-otp")
    public ResponseEntity<Map<String, String>> sendRegistrationOtp(@Valid @RequestBody RegisterOtpRequest req) {
        authService.sendRegistrationOtp(req);
        return ResponseEntity.ok(Map.of("message", "Verification code sent"));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    // Clears the single-session flag (see AuthService.login()) so the account
    // can sign in elsewhere immediately. auth is null if no valid access token
    // was presented (e.g. it already expired) — nothing to clear in that case,
    // so this just no-ops rather than erroring; logout should never fail from
    // the caller's point of view.
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(Authentication auth) {
        if (auth != null) authService.logout(auth.getName());
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok(Map.of("message", "If that email exists, an OTP has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}
