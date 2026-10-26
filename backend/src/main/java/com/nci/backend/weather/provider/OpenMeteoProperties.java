package com.nci.backend.weather.provider;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated
@ConfigurationProperties("custerra.providers.open-meteo")
public record OpenMeteoProperties(@NotNull URI url, @Min(100) @Max(30000) int connectTimeoutMs,
                                 @Min(100) @Max(30000) int readTimeoutMs) {
    @AssertTrue(message = "Weather URL must use HTTPS without credentials, query or fragment")
    public boolean isUrlValid() {
        return url != null && "https".equals(url.getScheme()) && url.getHost() != null
                && url.getUserInfo() == null && url.getQuery() == null && url.getFragment() == null;
    }
}
