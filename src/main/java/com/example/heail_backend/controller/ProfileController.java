package com.example.heail_backend.controller;

import com.example.heail_backend.dto.AuthResponse;
import com.example.heail_backend.dto.ProfileOtpRequest;
import com.example.heail_backend.dto.ProfileResponse;
import com.example.heail_backend.dto.UpdateProfileRequest;
import com.example.heail_backend.dto.VerifyProfileOtpRequest;
import com.example.heail_backend.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/me/profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileResponse> get(Authentication auth) {
        return ResponseEntity.ok(profileService.getProfile(auth.getName()));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody ProfileOtpRequest req, Authentication auth) {
        profileService.sendOtp(auth.getName(), req);
        return ResponseEntity.ok(Map.of("message", "Verification code sent"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@Valid @RequestBody VerifyProfileOtpRequest req, Authentication auth) {
        profileService.verifyOtp(auth.getName(), req);
        return ResponseEntity.ok(Map.of("message", "Verified"));
    }

    @PutMapping
    public ResponseEntity<AuthResponse> update(@Valid @RequestBody UpdateProfileRequest req, Authentication auth) {
        return ResponseEntity.ok(profileService.updateProfile(auth.getName(), req));
    }
}
