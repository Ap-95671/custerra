package com.nci.backend.intelligence;

import com.nci.backend.provider.ProviderException;
import com.nci.backend.provider.ProviderHttp;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

@RestController
@RequestMapping("/api/v1/intelligence")
public class RainfallContextController {
    private static final String SOURCE_URL = "https://open-meteo.com/en/docs/historical-weather-api";
    private static final int DAYS = 7;

    public record Day(LocalDate date, Double rainMm) {}
    public record Rainfall(List<Day> days, Double totalRainMm, boolean incomplete,
                           double gridLatitude, double gridLongitude, Instant retrievedAt, String source, String sourceUrl) {}
    private record LocationDay(double latitude, double longitude, LocalDate end) {}

    private final Clock clock;
    private final ObjectMapper json;
    private final RestClient client = ProviderHttp.client(3000, 10000);
    private final LinkedHashMap<LocationDay, Rainfall> cache = new LinkedHashMap<>();
    private Instant nextRequest = Instant.MIN;
    private boolean requestInFlight;

    public RainfallContextController(Clock clock, ObjectMapper json) {
        this.clock = clock;
        this.json = json;
    }

    @GetMapping("/rainfall")
    public Rainfall latest(@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
                                        @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var now = clock.instant();
        // End eight UTC days back to allow the source's reanalysis publication lag.
        var end = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(8);
        var start = end.minusDays(DAYS - 1);
        var key = new LocationDay(latitude, longitude, end);
        synchronized (cache) {
            var saved = cache.get(key);
            if (saved != null && saved.retrievedAt().plus(12, ChronoUnit.HOURS).isAfter(now)) return saved;
            if (requestInFlight || now.isBefore(nextRequest)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            requestInFlight = true;
            nextRequest = now.plusSeconds(1);
        }
        try {
            var uri = URI.create("https://archive-api.open-meteo.com/v1/archive?latitude=" + latitude + "&longitude=" + longitude
                    + "&start_date=" + start + "&end_date=" + end + "&daily=rain_sum&models=era5&timezone=GMT");
            var bytes = ProviderHttp.get(client, uri, "application/json", 65536);
            var rainfall = parse(json.readTree(bytes), start, clock.instant());
            synchronized (cache) {
                cache.remove(key);
                cache.put(key, rainfall);
                if (cache.size() > 200) cache.pollFirstEntry();
            }
            return rainfall;
        } catch (JacksonException malformed) {
            throw new ProviderException(false);
        } finally {
            synchronized (cache) { requestInFlight = false; }
        }
    }

    static Rainfall parse(JsonNode root, LocalDate start, Instant retrievedAt) {
        var units = root.path("daily_units");
        var daily = root.path("daily");
        var dates = daily.path("time");
        var values = daily.path("rain_sum");
        var latitude = root.path("latitude");
        var longitude = root.path("longitude");
        if (!"mm".equals(units.path("rain_sum").asText()) || !dates.isArray() || !values.isArray()
                || dates.size() != DAYS || values.size() != DAYS) throw new ProviderException(false);
        if (!latitude.isNumber() || !longitude.isNumber()
                || !Double.isFinite(latitude.asDouble()) || Math.abs(latitude.asDouble()) > 90
                || !Double.isFinite(longitude.asDouble()) || Math.abs(longitude.asDouble()) > 180) throw new ProviderException(false);

        var days = new ArrayList<Day>(DAYS);
        boolean incomplete = false;
        double total = 0;
        for (int index = 0; index < DAYS; index++) {
            var date = start.plusDays(index);
            if (!date.toString().equals(dates.get(index).asText())) throw new ProviderException(false);
            var value = values.get(index);
            if (!value.isNull() && (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0))
                throw new ProviderException(false);
            var rain = value.isNull() ? null : value.asDouble();
            days.add(new Day(date, rain));
            if (rain == null) incomplete = true;
            else total += rain;
        }
        if (!Double.isFinite(total)) throw new ProviderException(false);
        return new Rainfall(List.copyOf(days), incomplete ? null : total, incomplete, latitude.asDouble(), longitude.asDouble(), retrievedAt,
                "Open-Meteo / ECMWF ERA5 reanalysis", SOURCE_URL);
    }
}
