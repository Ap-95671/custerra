package com.nci.backend.common.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String error, String message,
                       String path, List<ValidationError> validationErrors) {
    public record ValidationError(String field, String message) {}
}
