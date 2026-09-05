package com.example.heail_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A superadmin override of one {@link com.example.heail_backend.email.EmailTemplateType}'s
 * subject/body. No row = the built-in default is used; deleting the row resets to default.
 */
@Entity
@Table(name = "email_template")
@Data
public class EmailTemplate {

    /** Matches EmailTemplateType.getKey(). */
    @Id
    @Column(name = "template_key")
    String key;

    @Column(name = "subject", nullable = false, columnDefinition = "text")
    String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    String body;

    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    String updatedBy;
}
