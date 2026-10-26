package com.nci.backend.provider.usgs;

import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("custerra.providers.usgs")
public record UsgsProperties(@NotNull URI queryUrl,
        @Min(100) @Max(30000) int connectTimeoutMs, @Min(100) @Max(30000) int readTimeoutMs) {
    @AssertTrue(message = "USGS query URL must be HTTPS without user info, query or fragment")
    public boolean isUrlValid() {
        return queryUrl != null && "https".equals(queryUrl.getScheme()) && queryUrl.getHost() != null
                && queryUrl.getUserInfo() == null && queryUrl.getQuery() == null && queryUrl.getFragment() == null;
    }
}
