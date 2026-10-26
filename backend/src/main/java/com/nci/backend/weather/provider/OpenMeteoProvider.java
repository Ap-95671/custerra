package com.nci.backend.weather.provider;
import com.nci.backend.weather.dto.WeatherResponse;
import com.nci.backend.weather.model.WeatherQuery;
import com.nci.backend.provider.ProviderException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
@Component
public class OpenMeteoProvider implements WeatherProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenMeteoProvider.class);
    private final OpenMeteoProperties properties;
    private final OpenMeteoMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final RestClient client;
    @Autowired
    public OpenMeteoProvider(OpenMeteoProperties properties, OpenMeteoMapper mapper, ObjectMapper json, Clock clock) {
        this(properties, mapper, json, clock, client(properties));
    }
    OpenMeteoProvider(OpenMeteoProperties properties, OpenMeteoMapper mapper, ObjectMapper json, Clock clock, RestClient client) {
        this.properties = properties; this.mapper = mapper; this.json = json; this.clock = clock; this.client = client;
    }
    private static RestClient client(OpenMeteoProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeoutMs()); factory.setReadTimeout(properties.readTimeoutMs());
        return RestClient.builder().requestFactory(factory).build();
    }
    @Override
    public WeatherResponse fetch(WeatherQuery query) {
        var uri = UriComponentsBuilder.fromUri(properties.url())
                .queryParam("latitude", query.latitude()).queryParam("longitude", query.longitude())
                .queryParam("current", "temperature_2m,rain,precipitation,wind_speed_10m")
                .queryParam("temperature_unit", "celsius").queryParam("wind_speed_unit", "kmh")
                .queryParam("precipitation_unit", "mm").queryParam("timezone", "GMT")
                .queryParam("timeformat", "unixtime").queryParam("forecast_days", 1).build().encode().toUri();
        try {
            byte[] bytes = client.get().uri(uri).header("Accept", "application/json").exchange((request, response) -> {
                if (response.getStatusCode().value() != 200) throw new ProviderException(false);
                var body = response.getBody().readNBytes(65537);
                if (body.length > 65536) throw new ProviderException(false);
                return body;
            });
            return mapper.map(json.readValue(bytes, OpenMeteoPayload.class), query, clock.instant());
        } catch (ProviderException failure) {
            log.warn("Weather provider failed: source=OPEN_METEO status={}", failure.getStatusCode().value());
            throw failure;
        } catch (ResourceAccessException failure) {
            boolean timeout = failure.getMostSpecificCause() instanceof SocketTimeoutException;
            log.warn("Weather transport failure: source=OPEN_METEO timeout={}", timeout);
            throw new ProviderException(timeout);
        } catch (RuntimeException failure) {
            log.warn("Weather payload rejected: source=OPEN_METEO type={}", failure.getClass().getSimpleName());
            throw new ProviderException(false);
        }
    }
}
