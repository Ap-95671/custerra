package com.nci.backend;

import com.nci.backend.config.CorsConfig;
import com.nci.backend.health.controller.HealthController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(HealthController.class)
@Import({CorsConfig.class, NaturalCalamitiesApplicationTests.ValidationProbe.class})
class NaturalCalamitiesApplicationTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void healthReturnsPublicDto() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\",\"service\":\"Custerra API\"}"));
    }

    @Test
    void unknownEndpointUsesErrorContract() throws Exception {
        mvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/unknown"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        mvc.perform(post("/api/v1/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void invalidBodyReturnsFieldErrorsWithoutRejectedValues() throws Exception {
        mvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors[0].field").value("name"))
                .andExpect(jsonPath("$.validationErrors[0].rejectedValue").doesNotExist());
    }

    @Test
    void malformedJsonUsesSafeError() throws Exception {
        mvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{secret:broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bad Request"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void validBodyIsAccepted() throws Exception {
        mvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Custerra\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void missingAndInvalidParametersReturn400() throws Exception {
        for (String uri : new String[]{"/test/coordinate", "/test/coordinate?latitude=oops",
                "/test/coordinate?latitude=91", "/test/coordinate?latitude=-91"}) {
            mvc.perform(get(uri))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void unexpectedExceptionDoesNotExposeDetails() throws Exception {
        mvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal Server Error"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("sensitive"))));
    }

    @Test
    void configuredOriginCanReadApi() throws Exception {
        mvc.perform(options("/api/v1/health").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void unconfiguredOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/health").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // Test-only routes exercise the shared infrastructure without inventing product APIs.
    @RestController
    static class ValidationProbe {
        record Input(@NotBlank String name) {}

        @PostMapping("/test/validation")
        Input validate(@Valid @RequestBody Input input) { return input; }

        @GetMapping("/test/coordinate")
        int coordinate(@RequestParam @Min(-90) @Max(90) int latitude) { return latitude; }

        @GetMapping("/test/failure")
        void fail() { throw new IllegalStateException("sensitive internal information"); }
    }
}
