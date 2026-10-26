package com.nci.backend.provider.gdacs;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("custerra.providers.gdacs")
public record GdacsProperties(boolean enabled, @Min(100) @Max(30000) int connectTimeoutMs,
                             @Min(100) @Max(30000) int readTimeoutMs) {}
