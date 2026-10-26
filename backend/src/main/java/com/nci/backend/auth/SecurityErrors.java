package com.nci.backend.auth;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.nci.backend.common.exception.ApiError;
@Component
public class SecurityErrors {
    private final ObjectMapper json;
    public SecurityErrors(ObjectMapper json) { this.json=json; }
    public void write(HttpServletRequest request,HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setHeader("Cache-Control","no-store");
        response.getWriter().write(json.writeValueAsString(new ApiError(Instant.now(),status,HttpStatus.valueOf(status).getReasonPhrase(),message,request.getRequestURI(),List.of())));
    }
}
