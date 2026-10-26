package com.nci.backend.route.provider;

import com.nci.backend.provider.ProviderException;
import com.nci.backend.route.dto.RoutePoint;
import java.net.*;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PlanningProviderTests {
    final PlanningProperties props=new PlanningProperties(URI.create("https://example.test/search"),URI.create("https://example.test/route/v1/driving"),1000,1000);
    final Clock clock=Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"),ZoneOffset.UTC);
    final RoutePoint from=new RoutePoint(0.,0.),to=new RoutePoint(0.,1.);
    final String geometry="???_c`|@"; // (0,0) -> (0,1), polyline precision 6.
    MockRestServiceServer server; RestClient client;
    @BeforeEach void setup() { var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();client=builder.build(); }
    OpenMeteoGeocodingProvider geocoder() { return new OpenMeteoGeocodingProvider(props,new JsonMapper(),clock,client); }
    OsrmRouteProvider router() { return new OsrmRouteProvider(props,new JsonMapper(),clock,client); }
    String route(String code,String distance) { return "{\"code\":\""+code+"\",\"routes\":[{\"geometry\":\""+geometry+"\",\"distance\":"+distance+",\"duration\":7200,\"weight_name\":\"routability\"}]}"; }
    @Test void geocodingNormalizesCandidatesAndEncodesSearchAsOneParameter() {
        server.expect(request->assertThat(request.getURI().getRawQuery()).contains("name=Delhi%26count%3D100", "count=5"))
                .andRespond(withSuccess("{\"results\":[{\"id\":1,\"name\":\"Delhi\",\"latitude\":28.6,\"longitude\":77.2,\"country\":\"India\",\"population\":1000}],\"generationtime_ms\":0.2}",MediaType.APPLICATION_JSON));
        var result=geocoder().search("Delhi&count=100");
        assertThat(result.candidates().getFirst().location()).isEqualTo(new RoutePoint(28.6,77.2));
        assertThat(result.candidates().getFirst().region()).isNull();
        assertThat(result.source().attribution()).contains("GeoNames"); server.verify();
    }
    @Test void emptyGeocodingDiffersFromMalformedOrOutOfRangeResults() {
        server.expect(anything()).andRespond(withSuccess("{\"generationtime_ms\":0.2}",MediaType.APPLICATION_JSON));
        assertThat(geocoder().search("Nothing").candidates()).isEmpty();
        for(String body:new String[]{"{}","not-json","{\"results\":[{\"id\":1,\"name\":\"x\",\"latitude\":91,\"longitude\":0}]}"}) {
            server.reset();server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThatThrownBy(()->geocoder().search("Place")).isInstanceOf(ProviderException.class);
        }
    }
    @Test void routeRetainsFullEncodedGeometryAndSourceMetrics() {
        server.expect(request->assertThat(request.getURI().toString()).contains("0.0,0.0;1.0,0.0", "overview=full", "geometries=polyline6"))
                .andRespond(withSuccess(route("Ok","112000"),MediaType.APPLICATION_JSON));
        var result=router().route(from,to,RouteProvider.TravelMode.DRIVING);
        assertThat(result.points()).containsExactly(from,to);assertThat(result.distanceKm()).isEqualTo(112);
        assertThat(result.durationSeconds()).isEqualTo(7200);assertThat(result.geometry()).isEqualTo(geometry);
        assertThat(result.retrievedAt()).isEqualTo(clock.instant());server.verify();
    }
    @Test void noRouteExcessiveLengthOrSnapNeverInventAPath() {
        for(String body:new String[]{route("NoRoute","112000"),route("Ok","901000")}) {
            server.reset();server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThatThrownBy(()->router().route(from,to,RouteProvider.TravelMode.DRIVING)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(422));
        }
        server.reset();server.expect(anything()).andRespond(withSuccess(route("Ok","112000"),MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->router().route(new RoutePoint(10.,0.),to,RouteProvider.TravelMode.DRIVING)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void malformedGeometryAndResponsesFailWithoutPretendingNoHazards() {
        for(String body:new String[]{"{}","not-json",route("Ok","null"),route("Ok","112000").replace(geometry,"??"),"x".repeat(1_000_001)}) {
            server.reset();server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
            assertThatThrownBy(()->router().route(from,to,RouteProvider.TravelMode.DRIVING)).isInstanceOf(ProviderException.class);
        }
        assertThatThrownBy(()->EncodedRoute.decode("~~~~~~~??")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void providersHandleHttpErrorAndTimeout() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("private")));
        assertThatThrownBy(()->geocoder().search("Delhi")).isInstanceOf(ProviderException.class);
        assertThatThrownBy(()->router().route(from,to,RouteProvider.TravelMode.DRIVING)).isInstanceOfSatisfying(ProviderException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(504));
    }
}
