package com.example.heail_backend.controller;

import com.example.heail_backend.dto.EmailTemplateDto;
import com.example.heail_backend.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Superadmin editing of transactional email wording.
 *
 * <ul>
 *   <li>GET  /api/v1/admin/email-templates              — all types with effective + default text</li>
 *   <li>PUT  /api/v1/admin/email-templates/{key}        — body {subject, body} — save an override</li>
 *   <li>POST /api/v1/admin/email-templates/{key}/reset  — drop the override, back to the built-in default</li>
 *   <li>POST /api/v1/admin/email-templates/{key}/test   — send this template (sample values) to ?email= or yourself</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/email-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminEmailTemplateController {

    private final EmailTemplateService service;

    @GetMapping
    public ResponseEntity<List<EmailTemplateDto>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PutMapping("/{key}")
    public ResponseEntity<EmailTemplateDto> update(@PathVariable String key,
                                                   @RequestBody Map<String, String> body,
                                                   Authentication auth) {
        return ResponseEntity.ok(service.update(key, body.get("subject"), body.get("body"), auth.getName()));
    }

    @PostMapping("/{key}/reset")
    public ResponseEntity<EmailTemplateDto> reset(@PathVariable String key) {
        return ResponseEntity.ok(service.reset(key));
    }

    @PostMapping("/{key}/test")
    public ResponseEntity<Map<String, String>> test(@PathVariable String key,
                                                    @RequestParam(required = false) String email,
                                                    Authentication auth) {
        String to = (email != null && !email.isBlank()) ? email.trim() : auth.getName();
        service.sendTest(key, to);
        return ResponseEntity.ok(Map.of("message", "Test email sent to " + to));
    }
}
