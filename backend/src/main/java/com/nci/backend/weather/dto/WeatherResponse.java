package com.nci.backend.weather.dto;
import com.nci.backend.weather.model.WeatherQuery;
import com.nci.backend.provider.model.ProviderMetadata;
import java.time.Instant;
import java.util.List;
public record WeatherResponse(WeatherQuery query, double gridLatitude, double gridLongitude,
        String informationType, Instant validAt, Instant fetchedAt, int intervalSeconds,
        Double temperatureC, Double rainMm, Double precipitationMm, Double windSpeedKmh,
        boolean partial, boolean stale, ProviderMetadata source, String license, List<String> limitations) {
    public WeatherResponse { limitations = List.copyOf(limitations); }
}
