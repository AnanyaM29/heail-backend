package com.example.heail_backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handle(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handle(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handle(AccessDeniedException e) {
        String message = (e.getMessage() != null && !e.getMessage().isBlank())
                ? e.getMessage() : "You do not have access to this resource";
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handle(DataIntegrityViolationException e) {
        log.error("Data integrity violation", e);
        String root = e.getMostSpecificCause().getMessage();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("Data integrity error: " + root));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a
                ));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Validation failed");
        body.put("message", "Validation failed");
        body.put("fields", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {
        log.error("Unhandled exception", e);
        String detail = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body(detail));
    }

    private Map<String, String> body(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("message", message);
        return body;
    }
}
