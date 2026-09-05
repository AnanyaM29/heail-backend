package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** One email type for the admin console: its effective (possibly overridden) text,
 *  the built-in default for comparison/reset, and the placeholders it supports. */
@Data
public class EmailTemplateDto {
    String key;
    String label;
    List<String> placeholders;
    String subject;
    String body;
    String defaultSubject;
    String defaultBody;
    boolean overridden;
    LocalDateTime updatedAt;
    String updatedBy;
}
