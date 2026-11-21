package com.nci.backend.intelligence;
import java.time.Instant;
import java.util.List;
import com.nci.backend.common.exception.ApiError;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
@RestControllerAdvice(assignableTypes={IntelligenceController.class,ComparisonController.class})
public class IntelligenceErrors {
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<ApiError> invalid(HttpServletRequest request){return ResponseEntity.badRequest().body(new ApiError(Instant.now(),400,"Bad Request","Check coordinates, geometry and request limits.",request.getRequestURI(),List.of()));}
}
