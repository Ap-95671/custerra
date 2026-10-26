package com.nci.backend.common.exception;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        String reason = knownStatus == null ? "Request failed" : knownStatus.getReasonPhrase();
        List<ApiError.ValidationError> errors = List.of();
        String message = reason;
        if (exception instanceof MethodArgumentNotValidException invalid) {
            message = "Request validation failed";
            errors = invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> new ApiError.ValidationError(error.getField(), error.getDefaultMessage()))
                    .toList();
        }
        ApiError error = new ApiError(Instant.now(), status.value(), reason, message, path(request), errors);
        return new ResponseEntity<>(error, headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        // Exception messages may contain provider URLs or credentials; log only the type.
        log.error("Unhandled API failure: type={}", exception.getClass().getName());
        return handleExceptionInternal(exception, null, new HttpHeaders(),
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
