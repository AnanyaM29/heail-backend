package com.example.heail_backend.service;

import com.example.heail_backend.dto.EmailTemplateDto;
import com.example.heail_backend.email.EmailTemplateType;
import com.example.heail_backend.entity.EmailTemplate;
import com.example.heail_backend.repository.EmailTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin-console CRUD over email wording — reads the effective (default or overridden)
 *  text for every {@link EmailTemplateType}, saves/clears overrides, and fires test sends. */
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final EmailTemplateRepository repo;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<EmailTemplateDto> list() {
        Map<String, EmailTemplate> overrides = repo.findAll().stream()
                .collect(Collectors.toMap(EmailTemplate::getKey, Function.identity()));
        return Arrays.stream(EmailTemplateType.values())
                .map(type -> toDto(type, overrides.get(type.getKey())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmailTemplateDto get(String key) {
        EmailTemplateType type = EmailTemplateType.byKey(key);
        return toDto(type, repo.findById(key).orElse(null));
    }

    @Transactional
    public EmailTemplateDto update(String key, String subject, String body, String actor) {
        EmailTemplateType type = EmailTemplateType.byKey(key);
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Subject is required");
        if (body == null || body.isBlank()) throw new IllegalArgumentException("Body is required");

        EmailTemplate t = repo.findById(key).orElseGet(EmailTemplate::new);
        t.setKey(key);
        t.setSubject(subject.strip());
        t.setBody(body);
        t.setUpdatedAt(LocalDateTime.now());
        t.setUpdatedBy(actor);
        repo.save(t);
        return toDto(type, t);
    }

    /** Deletes the override so the built-in default is used again. */
    @Transactional
    public EmailTemplateDto reset(String key) {
        EmailTemplateType type = EmailTemplateType.byKey(key);
        repo.deleteById(key);
        return toDto(type, null);
    }

    public void sendTest(String key, String toEmail) {
        emailService.sendTest(EmailTemplateType.byKey(key), toEmail);
    }

    private EmailTemplateDto toDto(EmailTemplateType type, EmailTemplate override) {
        EmailTemplateDto d = new EmailTemplateDto();
        d.setKey(type.getKey());
        d.setLabel(type.getLabel());
        d.setPlaceholders(type.getPlaceholders());
        d.setDefaultSubject(type.getDefaultSubject());
        d.setDefaultBody(type.getDefaultBody());
        d.setSubject(override != null ? override.getSubject() : type.getDefaultSubject());
        d.setBody(override != null ? override.getBody() : type.getDefaultBody());
        d.setOverridden(override != null);
        if (override != null) {
            d.setUpdatedAt(override.getUpdatedAt());
            d.setUpdatedBy(override.getUpdatedBy());
        }
        return d;
    }
}
