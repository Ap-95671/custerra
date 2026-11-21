package com.nci.backend.intelligence;

import com.nci.backend.hazard.service.GeoDistance;
import com.nci.backend.route.dto.RoutePoint;
import com.nci.backend.route.service.RouteGeometry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RoadClosuresTests {
    private static final Instant NOW = Instant.parse("2026-11-19T00:15:00Z");
    private static final List<RoutePoint> BAY_ROUTE = List.of(new RoutePoint(37.77, -122.415), new RoutePoint(37.79, -122.415));
    private final ObjectMapper json = new ObjectMapper();
    private final RestClient.Builder client = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
    private final RoadClosures closures = new RoadClosures(json, new RouteGeometry(new GeoDistance()), Clock.fixed(NOW, ZoneOffset.UTC), client.build());

    @Test void returnsBoundedEndpointMatchesWithSourceStateAndUsesOneDistrictRead() {
        server.expect(requestTo(RoadClosures.DISTRICT_FEED)).andRespond(withSuccess(feed(
                row("active", -60, -60, 60, true, false, false, "37.78"),
                row("planned", -60, 120, 3_600, false, false, false, "37.775"),
                row("not-confirmed", -60, -20, 3_600, false, false, false, "37.78"),
                row("canceled", -70, -60, 60, true, false, false, "37.78"),
                row("canceled", -60, -60, 60, true, false, true, "37.78"),
                row("reopened", -30, -60, 60, true, true, false, "37.78"),
                row("elapsed-plan", -60, -60, -30, false, false, false, "37.78"),
                row("distant", -60, -60, 60, true, false, false, "38.8"),
                row("malformed", -60, -60, 60, true, false, false, "NaN")
        ), MediaType.APPLICATION_JSON));

        var result = closures.near(BAY_ROUTE);
        assertThat(result.coverage()).isEqualTo("PARTIAL");
        assertThat(result.sourceUpdatedAt()).isEqualTo(NOW.minusSeconds(30));
        assertThat(result.rejectedRecords()).isOne();
        assertThat(result.items()).extracting(RoadClosures.Match::id).containsExactlyInAnyOrder("active", "planned", "not-confirmed");
        assertThat(result.items()).extracting(RoadClosures.Match::state).containsExactlyInAnyOrder("ACTIVE", "SCHEDULED", "UNCONFIRMED");
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.distanceKm()).isLessThan(0.5);
            assertThat(item.segmentIndex()).isZero();
            assertThat(item.reportedRoad()).isEqualTo("US-101");
        });
        assertThat(closures.near(BAY_ROUTE)).isEqualTo(result);
        server.verify();
    }

    @Test void outsideDistrictAndStaleOrFailedSourcesAreExplicit() {
        assertThat(closures.near(List.of(new RoutePoint(52.5, 13.4), new RoutePoint(52.5, 13.1))).coverage()).isEqualTo("OUTSIDE_SCOPE");
        server.expect(requestTo(RoadClosures.DISTRICT_FEED)).andRespond(withSuccess(feed(
                row("old", -3600, -3700, 500, true, false, false, "37.78")
        ), MediaType.APPLICATION_JSON));
        var stale = closures.near(BAY_ROUTE);
        assertThat(stale.coverage()).isEqualTo("STALE");
        assertThat(stale.sourceUpdatedAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(stale.items()).isEmpty();
        server.verify();
    }

    @Test void upstreamFailureBacksOffAndReturnsUnavailable() {
        server.expect(requestTo(RoadClosures.DISTRICT_FEED)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThat(closures.near(BAY_ROUTE).coverage()).isEqualTo("UNAVAILABLE");
        assertThat(closures.near(BAY_ROUTE).coverage()).isEqualTo("UNAVAILABLE");
        server.verify();
    }

    private String feed(String... rows) { return "{\"data\":[" + String.join(",", rows) + "]}"; }

    private String row(String id, int source, int start, int end, boolean started, boolean reopened, boolean canceled, String lat) {
        return """
                {"lcs":{"index":"%s","recordTimestamp":{"recordEpoch":"%d"},
                 "location":{"travelFlowDirection":"North","begin":{"beginLatitude":"%s","beginLongitude":"-122.415","beginRoute":"US-101","beginLocationName":"Example junction"},
                 "end":{"endLatitude":"%s","endLongitude":"-122.415","endRoute":"US-101","endLocationName":"Example road"}},
                 "closure":{"facility":"Freeway","typeOfClosure":"Lane","typeOfWork":"Maintenance",
                 "code1097":{"isCode1097":"%s"},"code1098":{"isCode1098":"%s"},"code1022":{"isCode1022":"%s"},
                 "closureTimestamp":{"closureStartEpoch":"%d","closureEndEpoch":"%d","isClosureEndIndefinite":"false"}}}}
                """.formatted(id, NOW.plusSeconds(source).getEpochSecond(), lat, lat, started, reopened, canceled,
                        NOW.plusSeconds(start).getEpochSecond(), NOW.plusSeconds(end).getEpochSecond());
    }
}
