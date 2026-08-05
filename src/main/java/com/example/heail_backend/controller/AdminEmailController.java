package com.example.heail_backend.controller;

import com.example.heail_backend.service.EmailKillSwitch;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * TEMPORARY, deliberately unauthenticated (see SecurityConfig — this path is
 * permitAll'd) so the home-page "stop all emails" button can call it without
 * requiring login. Remove this controller, the EmailKillSwitch bean, and the
 * permitAll entry together once it's no longer needed.
 */
@RestController
@RequestMapping("/api/v1/admin/emails")
@RequiredArgsConstructor
public class AdminEmailController {

    private final EmailKillSwitch killSwitch;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("enabled", killSwitch.isEnabled()));
    }

    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disable() {
        killSwitch.setEnabled(false);
        return ResponseEntity.ok(Map.of("enabled", false, "message", "All outbound email is now stopped"));
    }

    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enable() {
        killSwitch.setEnabled(true);
        return ResponseEntity.ok(Map.of("enabled", true, "message", "Outbound email resumed"));
    }
}
