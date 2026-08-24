package com.example.heail_backend.controller;

import com.example.heail_backend.dto.ContactOtpRequest;
import com.example.heail_backend.dto.ContactSubmitRequest;
import com.example.heail_backend.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/contact")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody ContactOtpRequest req) {
        contactService.sendOtp(req.getEmail(), req.getMobile());
        return ResponseEntity.ok().body(Map.of("message", "Verification code sent"));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@Valid @RequestBody ContactSubmitRequest req) {
        contactService.submit(req.getName(), req.getMobile(), req.getEmail(), req.getCity(),
                req.getCountry(), req.getMessage(), req.getOtp());
        return ResponseEntity.ok().body(Map.of("message", "Message sent"));
    }
}
