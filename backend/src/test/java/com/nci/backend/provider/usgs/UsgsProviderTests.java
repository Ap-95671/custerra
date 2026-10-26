package com.nci.backend.provider.usgs;

import com.nci.backend.provider.ProviderException;
import com.nci.backend.provider.model.HazardQuery;
import com.nci.backend.hazard.model.*;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.*;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class UsgsProviderTests {
    private static final Instant NOW = Instant.parse("2026-01-08T00:00:00Z");
    private static final String FEATURE = """
        {"type":"Feature","id":"test-id","properties":{"type":"earthquake","title":"Test fixture",
         "time":1767225600000,"updated":1767225660000,"url":"https://earthquake.usgs.gov/earthquakes/eventpage/test-id",
         "mag":4.2,"tsunami":1,"alert":"red"},"geometry":{"type":"Point","coordinates":[179.9,10.0,12.5]}}
        """;
    private MockRestServiceServer server;
    private UsgsEarthquakeProvider provider;
    private final HazardQuery query = new HazardQuery(10, 179.9, 100, NOW.minus(Duration.ofDays(7)), NOW);

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new UsgsEarthquakeProvider(new UsgsProperties(
                URI.create("https://earthquake.usgs.gov/fdsnws/event/1/query"), 1000, 1000),
                new UsgsMapper(), JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC), builder.build());
    }

    private String feed(String features) { return "{\"type\":\"FeatureCollection\",\"features\":[" + features + "]}"; }

    @Test
    void sendsBoundedQueryAndNormalizesObservationWithoutInventingWarning() {
        server.expect(request -> {
            String q = request.getURI().getQuery();
            assertThat(q).contains("latitude=10.0", "longitude=179.9", "maxradiuskm=100.0", "limit=201",
                    "starttime=2026-01-01T00:00:00Z", "endtime=2026-01-08T00:00:00Z", "eventtype=earthquake");
        }).andRespond(withSuccess(feed(FEATURE), MediaType.APPLICATION_JSON));
        var result = provider.fetch(query);
        var event = result.events().getFirst();
        assertThat(event.latitude()).isEqualTo(10);
        assertThat(event.longitude()).isEqualTo(179.9);
        assertThat(event.startedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(event.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-01-01T00:01:00Z"));
        assertThat(event.observedAt()).isEqualTo(event.startedAt());
        assertThat(event.retrievedAt()).isEqualTo(NOW);
        assertThat(event.severity()).isEqualTo(SeverityLevel.UNKNOWN);
        assertThat(event.informationType()).isEqualTo(InformationType.OBSERVED_EVENT);
        assertThat(event.hazardType()).isEqualTo(HazardType.EARTHQUAKE);
        assertThat(event.affectedRadiusKm()).isNull();
        assertThat(event.measurements()).containsEntry("magnitude", 4.2).containsEntry("depthKm", 12.5);
        assertThat(result.truncated()).isFalse();
        server.verify();
    }

    @Test
    void emptyCollectionAnd204AreValidNoDataResponses() {
        server.expect(anything()).andRespond(withSuccess(feed(""), MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withNoContent());
        assertThat(provider.fetch(query).events()).isEmpty();
        assertThat(provider.fetch(query).events()).isEmpty();
    }

    @Test
    void nonEarthquakesAreExcludedAndMissingMagnitudeRemainsMissing() {
        server.expect(anything()).andRespond(withSuccess(feed(FEATURE.replace("\"earthquake\"", "\"quarry blast\"")), MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(feed(FEATURE.replace("4.2", "null")), MediaType.APPLICATION_JSON));
        assertThat(provider.fetch(query).events()).isEmpty();
        assertThat(provider.fetch(query).events().getFirst().measurements()).doesNotContainKey("magnitude");
    }

    @Test
    void detectsTruncationAndDeduplicatesProviderIds() {
        server.expect(anything()).andRespond(withSuccess(feed(String.join(",", Collections.nCopies(201, FEATURE))), MediaType.APPLICATION_JSON));
        var result = provider.fetch(query);
        assertThat(result.truncated()).isTrue();
        assertThat(result.events()).hasSize(1);
    }

    @Test
    void duplicateSourceIdentityKeepsNewestRevisionEvenWhenOlderRowArrivesLast() {
        server.expect(anything()).andRespond(withSuccess(feed(FEATURE.replace("1767225660000", "1767225720000") + "," + FEATURE), MediaType.APPLICATION_JSON));
        assertThat(provider.fetch(query).events().getFirst().sourceUpdatedAt()).isEqualTo(Instant.parse("2026-01-01T00:02:00Z"));
    }

    @Test
    void malformedOrOversizedResponsesFailInsteadOfAppearingEmpty() {
        for (String body : new String[]{"", "not-json", "{}", "{\"type\":\"FeatureCollection\"}",
                feed(FEATURE.replace("179.9", "181")), feed(FEATURE.replace("\"time\":1767225600000", "\"time\":null")),
                feed(FEATURE.replace("https://earthquake.usgs.gov", "javascript://untrusted")), "x".repeat(2_000_001)}) {
            server.reset();
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> provider.fetch(query)).isInstanceOfSatisfying(ProviderException.class,
                    e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
        }
    }

    @Test
    void upstreamFailuresAreSanitizedAndTimeoutsDistinguished() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("private upstream details"));
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("private timeout detail")));
        assertThatThrownBy(() -> provider.fetch(query)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
        assertThatThrownBy(() -> provider.fetch(query)).isInstanceOfSatisfying(ProviderException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(504));
    }

    @Test void historicalQueryUsesMagnitudeAndLargerBoundWithoutChangingRecentQueries() {
        server.expect(request -> assertThat(request.getURI().getQuery()).contains("limit=2001", "minmagnitude=4.0", "starttime=2025-01-08"))
                .andRespond(withSuccess(feed(FEATURE), MediaType.APPLICATION_JSON));
        var query = new com.nci.backend.history.HistoryQuery(10,179.9,100,NOW.minus(Duration.ofDays(365)),NOW,4);
        assertThat(provider.fetchHistory(query).events()).hasSize(1);
        server.verify();
    }
}
