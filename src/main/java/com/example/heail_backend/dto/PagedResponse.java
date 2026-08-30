package com.example.heail_backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Thin wrapper around a Spring Data {@link Page} for admin list endpoints.
 * Exists instead of returning {@code Page<T>} straight from the controller —
 * that works but Spring logs a warning on every request recommending against
 * serializing PageImpl directly, and this keeps the wire shape explicit and
 * stable for the frontend (content/page/size/totalElements/totalPages).
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
