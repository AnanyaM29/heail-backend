package com.example.heail_backend.controller;

import com.example.heail_backend.dto.ForgotPasswordRequest;
import com.example.heail_backend.service.PartnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody ForgotPasswordRequest req) {
        partnerService.sendOtp(req.getEmail());
        return ResponseEntity.ok().body(java.util.Map.of("message", "Verification code sent"));
    }

    @PostMapping(value = "/apply", consumes = "multipart/form-data")
    public ResponseEntity<?> apply(@RequestParam String name,
                                    @RequestParam String country,
                                    @RequestParam String city,
                                    @RequestParam String mobile,
                                    @RequestParam String email,
                                    @RequestParam String otp,
                                    @RequestParam boolean consentGiven,
                                    @RequestParam(required = false) MultipartFile resume) {
        partnerService.apply(name, country, city, mobile, email, otp, consentGiven, resume);
        return ResponseEntity.ok().body(java.util.Map.of("message", "Application submitted"));
    }
}
