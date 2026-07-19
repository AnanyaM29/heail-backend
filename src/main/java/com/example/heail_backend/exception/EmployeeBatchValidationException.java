package com.example.heail_backend.exception;

import com.example.heail_backend.dto.RowError;
import lombok.Getter;

import java.util.List;

@Getter
public class EmployeeBatchValidationException extends RuntimeException {

    private final List<RowError> rowErrors;

    public EmployeeBatchValidationException(List<RowError> rowErrors) {
        super("Employee batch validation failed");
        this.rowErrors = rowErrors;
    }
}
