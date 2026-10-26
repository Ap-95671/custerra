package com.nci.backend.weather.provider;
import com.nci.backend.provider.ProviderException;
import com.nci.backend.weather.model.WeatherQuery;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class OpenMeteoTests {
    static final String PAYLOAD = """
      {"latitude":32.25,"longitude":77.2,"utc_offset_seconds":0,
       "current_units":{"time":"unixtime","interval":"seconds","temperature_2m":"°C","rain":"mm","precipitation":"mm","wind_speed_10m":"km/h"},
       "current":{"time":1767225600,"interval":900,"temperature_2m":-2.5,"rain":1.2,"precipitation":2.3,"wind_speed_10m":12.4}}
      """;
    private MockRestServiceServer server;
    private OpenMeteoProvider provider;
    @BeforeEach void setup() {
        var builder = RestClient.builder(); server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenMeteoProvider(new OpenMeteoProperties(URI.create("https://api.open-meteo.com/v1/forecast"),1000,1000),
                new OpenMeteoMapper(), JsonMapper.builder().build(), Clock.fixed(Instant.parse("2026-01-01T00:10:00Z"),ZoneOffset.UTC),builder.build());
    }
    @Test void mapsUnitsEpochSecondsAndIntervalWithProvenance() {
        server.expect(request -> assertThat(request.getURI().getQuery()).contains("timeformat=unixtime","timezone=GMT","precipitation_unit=mm","wind_speed_unit=kmh","latitude=32.24"))
                .andRespond(withSuccess(PAYLOAD,MediaType.APPLICATION_JSON));
        var result = provider.fetch(new WeatherQuery(32.24,77.19));
        assertThat(result.validAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(result.temperatureC()).isEqualTo(-2.5); assertThat(result.intervalSeconds()).isEqualTo(900);
        assertThat(result.rainMm()).isEqualTo(1.2); assertThat(result.precipitationMm()).isEqualTo(2.3);
        assertThat(result.windSpeedKmh()).isEqualTo(12.4); assertThat(result.gridLatitude()).isEqualTo(32.25);
        assertThat(result.query().latitude()).isEqualTo(32.24); assertThat(result.informationType()).isEqualTo("MODELED_WEATHER");
        assertThat(result.source().id()).isEqualTo("OPEN_METEO"); assertThat(result.stale()).isFalse(); server.verify();
    }
    @Test void missingValuesStayNullRatherThanZero() {
        server.expect(anything()).andRespond(withSuccess(PAYLOAD.replace("\"rain\":1.2","\"rain\":null"),MediaType.APPLICATION_JSON));
        var result = provider.fetch(new WeatherQuery(0,0));
        assertThat(result.rainMm()).isNull(); assertThat(result.partial()).isTrue();
    }
    @Test void staleValuesAreFlagged() {
        server.expect(anything()).andRespond(withSuccess(PAYLOAD.replace("1767225600","1767139200"),MediaType.APPLICATION_JSON));
        assertThat(provider.fetch(new WeatherQuery(0,0)).stale()).isTrue();
    }
    @Test void wrongUnitsMalformedPayloadNegativeAmountsAndFutureTimesFail() {
        for (String body : new String[]{"", "{}", PAYLOAD.replace("km/h","mph"), PAYLOAD.replace("\"rain\":1.2","\"rain\":-1"),
                PAYLOAD.replace("1767225600","1767312000"), PAYLOAD.replace("\"interval\":900","\"interval\":0"), "x".repeat(65537)}) {
            server.reset(); server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> provider.fetch(new WeatherQuery(0,0))).isInstanceOfSatisfying(ProviderException.class,
                    e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
        }
    }
    @Test void upstreamFailuresAndTimeoutsAreSanitized() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("secret")));
        for (int status : new int[]{502,504}) assertThatThrownBy(() -> provider.fetch(new WeatherQuery(0,0)))
                .isInstanceOfSatisfying(ProviderException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(status));
    }
    @Test void validatesCoordinatesAndProviderConfiguration() {
        assertThatThrownBy(() -> new WeatherQuery(Double.NaN,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeatherQuery(0,181)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new OpenMeteoProperties(URI.create("http://example.test"),1000,1000).isUrlValid()).isFalse();
        assertThat(new OpenMeteoProperties(URI.create("https://example.test?secret=value"),1000,1000).isUrlValid()).isFalse();
    }
}
