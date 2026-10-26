package com.nci.backend.provider.firms;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("custerra.providers.firms")
public record FirmsProperties(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{0,128}") String mapKey,
        @Min(100) @Max(30000) int connectTimeoutMs, @Min(100) @Max(30000) int readTimeoutMs) {
    @Override public String toString() { return "FirmsProperties[mapKey=REDACTED]"; }
}
