package com.nci.backend.weather.provider;
import com.nci.backend.weather.dto.WeatherResponse;
import com.nci.backend.weather.model.WeatherQuery;
import com.nci.backend.provider.model.ProviderMetadata;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class OpenMeteoMapper {
    private static final Map<String, String> UNITS = Map.of("time", "unixtime", "interval", "seconds",
            "temperature_2m", "°C", "rain", "mm", "precipitation", "mm", "wind_speed_10m", "km/h");
    public WeatherResponse map(OpenMeteoPayload payload, WeatherQuery query, Instant fetchedAt) {
        if (payload == null || payload.current() == null || payload.current_units() == null
                || !payload.current_units().entrySet().containsAll(UNITS.entrySet())
                || !Integer.valueOf(0).equals(payload.utc_offset_seconds())
                || payload.latitude() == null || payload.longitude() == null)
            throw new IllegalArgumentException("Invalid weather envelope or units");
        new WeatherQuery(payload.latitude(), payload.longitude());
        var c = payload.current();
        if (c.time() == null || c.interval() == null || c.interval() < 1 || c.interval() > 3600)
            throw new IllegalArgumentException("Invalid weather interval");
        Instant validAt = Instant.ofEpochSecond(c.time());
        if (validAt.isAfter(fetchedAt.plusSeconds(900))) throw new IllegalArgumentException("Future current weather");
        check(c.temperature_2m(), false); check(c.rain(), true); check(c.precipitation(), true); check(c.wind_speed_10m(), true);
        boolean partial = c.temperature_2m() == null || c.rain() == null || c.precipitation() == null || c.wind_speed_10m() == null;
        return new WeatherResponse(query, payload.latitude(), payload.longitude(), "MODELED_WEATHER",
                validAt, fetchedAt, c.interval(), c.temperature_2m(), c.rain(), c.precipitation(), c.wind_speed_10m(),
                partial, Duration.between(validAt, fetchedAt).compareTo(Duration.ofHours(2)) > 0,
                new ProviderMetadata("OPEN_METEO", "Open-Meteo", "https://open-meteo.com/en/docs",
                        "https://open-meteo.com/en/terms", "On demand; model update schedules vary."), "CC BY 4.0",
                List.of("Model-based conditions, not a local station measurement or official warning.",
                        "Rain and precipitation are totals over the preceding provider interval, not daily totals.",
                        "Weather is context only; no flood, landslide or travel-safety inference is made."));
    }
    private void check(Double value, boolean nonnegative) {
        if (value != null && (!Double.isFinite(value) || (nonnegative && value < 0)))
            throw new IllegalArgumentException("Invalid weather measurement");
    }
}
