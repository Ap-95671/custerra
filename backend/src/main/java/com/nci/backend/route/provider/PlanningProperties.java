package com.nci.backend.route.provider;

import java.net.URI;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("custerra.providers.planning")
public record PlanningProperties(@NotNull URI geocodingUrl, @NotNull URI routingUrl,
        @Min(100) @Max(30000) int connectTimeoutMs, @Min(100) @Max(30000) int readTimeoutMs) {
    @AssertTrue(message="Planning URLs must be HTTPS without credentials, queries or fragments")
    public boolean isUrlsValid() { return valid(geocodingUrl) && valid(routingUrl); }
    private boolean valid(URI uri) {
        return uri != null && "https".equals(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
    }
}
