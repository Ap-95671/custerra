package com.nci.backend.intelligence;
import java.time.*;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import com.nci.backend.route.dto.RoutePoint;
import com.nci.backend.provider.ProviderException;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IntelligenceTests {
    final ObjectMapper json=new ObjectMapper();final SpatialGeometry spatial=new SpatialGeometry();
    static final Instant NOW=Instant.parse("2026-11-04T12:00:00Z");
    final Clock clock=Clock.fixed(NOW,ZoneOffset.UTC);
    IntelligenceEvent.Shape shape(String type,String coords){return new IntelligenceEvent.Shape(type,json.readTree(coords));}
    IntelligenceFeed parser(){return new IntelligenceFeed(json,clock,spatial,RestClient.create(),true,true);}
    String polygon="[[[-1,-1],[1,-1],[1,1],[-1,1],[-1,-1]],[[-0.5,-0.5],[-0.5,0.5],[0.5,0.5],[0.5,-0.5],[-0.5,-0.5]]]";
    String nws(String extra,String geometry){return "{\"type\":\"FeatureCollection\",\"features\":[{\"geometry\":"+geometry+",\"properties\":{\"id\":\"https://api.weather.gov/alerts/fixture\",\"status\":\"Actual\",\"messageType\":\"Alert\",\"event\":\"Flood Warning\",\"headline\":\"Synthetic official-source fixture\",\"sent\":\"2026-11-04T10:00:00Z\",\"effective\":\"2026-11-04T11:00:00Z\",\"expires\":\"2026-11-04T13:00:00Z\",\"severity\":\"Severe\",\"urgency\":\"Immediate\",\"certainty\":\"Observed\",\"areaDesc\":\"Fixture area\",\"senderName\":\"NWS fixture office\""+extra+"}}]}";}
    @Test void polygonHolesBoundaryAndRouteLengthAreGeometricNotImpact(){
        var geometry=shape("Polygon",polygon);
        assertThat(spatial.location(geometry,0,0).intersects()).isFalse();assertThat(spatial.location(geometry,0,1).intersects()).isTrue();
        var match=spatial.route(geometry,List.of(new RoutePoint(0.,-2.),new RoutePoint(0.,2.)));
        assertThat(match.intersects()).isTrue();assertThat(match.segmentIndices()).containsExactly(0);assertThat(match.lengthWithinKm()).isBetween(110.,112.);
    }
    @Test void datelinePolygonAndRouteRemainAdjacent(){
        var geometry=shape("Polygon","[[[179,-1],[-179,-1],[-179,1],[179,1],[179,-1]]]");
        assertThat(spatial.location(geometry,0,180).intersects()).isTrue();assertThat(spatial.location(geometry,0,0).intersects()).isFalse();
        var match=spatial.route(geometry,List.of(new RoutePoint(0.,178.),new RoutePoint(0.,-178.)));
        assertThat(match.lengthWithinKm()).isBetween(221.,224.);
    }
    @Test void invalidUnclosedSelfIntersectingAndOversizedGeometriesFail(){
        for(String coordinates:List.of("[[[0,0],[1,1],[1,0],[0,1],[0,0]]]","[[[0,0],[1,0],[1,1],[0,1]]]","[[[181,0],[1,0],[1,1],[181,0]]]"))assertThatThrownBy(()->spatial.read(shape("Polygon",coordinates))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->spatial.read(shape("Point","[\"NaN\",0]"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nullPolarAndTrackGeometryDoNotInventAreaOrZero(){
        assertThat(spatial.location(null,0,0).intersects()).isNull();
        assertThat(spatial.location(shape("Point","[0,89]"),89,0).distanceKm()).isNull();
        assertThat(spatial.route(shape("LineString","[[-1,0],[1,0]]"),List.of(new RoutePoint(-1.,0.),new RoutePoint(1.,0.))).lengthWithinKm()).isNull();
    }
    @Test void officialWarningPreservesSourceClassificationsAndMissingGeometry(){
        var result=parser().parseNws(json.readTree(nws(",\"language\":\"en-US\"","null")),NOW);var event=result.items().getFirst();
        assertThat(event.informationType()).isEqualTo("OFFICIAL_WARNING");assertThat(event.hazardType()).isEqualTo("FLOOD");assertThat(event.severity()).isEqualTo("Severe");assertThat(event.urgency()).isEqualTo("Immediate");assertThat(event.geometry()).isNull();assertThat(event.attributes()).containsEntry("geometryStatus","AREA_TEXT_ONLY");assertThat(result.coverageStatus()).isEqualTo("PARTIAL");
    }
    @Test void warningExpiryUpdateCancellationAndFutureEffectiveStatesAreExplicit(){
        for(String message:List.of("Alert","Update","Cancel")){
            var input=nws("","null").replace("\"messageType\":\"Alert\"","\"messageType\":\""+message+"\"");var event=parser().parseNws(json.readTree(input),NOW).items().getFirst();
            assertThat(event.stateAt(NOW)).isEqualTo(message.equals("Cancel")?"CANCELLED":"CURRENT");assertThat(event.stateAt(NOW.plusSeconds(7200))).isEqualTo(message.equals("Cancel")?"CANCELLED":"EXPIRED");
            if(!message.equals("Cancel"))assertThat(event.stateAt(NOW.minusSeconds(7200))).isEqualTo("UPCOMING");
        }
    }
    @Test void testMessagesInvalidGeometryAndImpersonatedDomainsAreNotOfficialRows(){
        assertThat(parser().parseNws(json.readTree(nws("","null").replace("Actual","Test")),NOW).items()).isEmpty();
        assertThat(parser().parseNws(json.readTree(nws(",\"web\":\"https://evilweather.gov/fake\"","null")),NOW).items()).isEmpty();
        assertThat(parser().parseNws(json.readTree(nws("","{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,1],[1,0],[0,1],[0,0]]]}")),NOW).items()).isEmpty();
    }
    @Test void eonetIsCuratedAndPreservesTimeOrderedTrackAndOriginalSources(){
        var tree=json.readTree("""
            {"events":[{"id":"fixture","title":"Synthetic volcano report","link":"https://eonet.gsfc.nasa.gov/api/v3/events/fixture","categories":[{"id":"volcanoes"}],"sources":[{"id":"scientific-source","url":"https://www.usgs.gov/example"}],"geometry":[{"date":"2026-11-03T00:00:00Z","type":"Point","coordinates":[1,2]},{"date":"2026-11-04T00:00:00Z","type":"Point","coordinates":[2,3]}]}]}
            """);var event=parser().parseEonet(tree,NOW).items().getFirst();assertThat(event.informationType()).isEqualTo("CURATED_EVENT");assertThat(event.hazardType()).isEqualTo("VOLCANIC_ERUPTION");assertThat(event.track()).hasSize(2);assertThat(event.geometry().coordinates().get(0).asDouble()).isEqualTo(2.);assertThat(event.sourceLinks().getFirst().id()).isEqualTo("scientific-source");
    }
    @Test void cachedFeedPreservesTimestampAndAvoidsLocationOrRepeatedUpstreamRequests(){
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.weather.gov/alerts/active?status=actual&area=CA")).andExpect(header("User-Agent","Custerra/0.1 (+https://github.com/Ap-95671/custerra)")).andRespond(withSuccess(nws("","null"),MediaType.APPLICATION_JSON));
        var feed=new IntelligenceFeed(json,clock,spatial,builder.build(),true,true);var first=feed.fetch("NWS");assertThat(feed.fetch("NWS")).isSameAs(first);server.verify();
    }
    @Test void providerFailureHasBackoffAndDoesNotReturnOldEvidenceAsFresh(){
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();server.expect(anything()).andRespond(withServerError());var feed=new IntelligenceFeed(json,clock,spatial,builder.build(),true,true);
        assertThatThrownBy(()->feed.fetch("NWS")).isInstanceOf(ProviderException.class);assertThatThrownBy(()->feed.fetch("NWS")).isInstanceOf(ProviderException.class);server.verify();
    }
    @Test void explanationsReferenceEvidenceWithoutAIOrForecast(){var e=parser().parseNws(json.readTree(nws("","null")),NOW).items().getFirst();var explanation=IntelligenceService.explain(e,"CURRENT");assertThat(explanation.mode()).isEqualTo("DETERMINISTIC_SOURCE_SUMMARY");assertThat(explanation.text()).contains("Overall risk is UNKNOWN",e.issuedAt().toString(),"Severe");assertThat(explanation.sources()).hasSize(1);assertThat(IntelligenceService.correlate(List.of(e))).isEmpty();}
    @Test void currentNwsUrnIdentityUsesCanonicalApiLinkAndOffsetTimestamps(){
        String input=nws(",\"@id\":\"https://api.weather.gov/alerts/urn:oid:fixture\",\"web\":\"http://www.weather.gov\"","null").replace("\"id\":\"https://api.weather.gov/alerts/fixture\"","\"id\":\"urn:oid:fixture\"").replace("2026-11-04T10:00:00Z","2026-11-04T02:00:00-08:00");
        var event=parser().parseNws(json.readTree(input),NOW).items().getFirst();assertThat(event.id()).isEqualTo("urn:oid:fixture");assertThat(event.sourceUrl()).isEqualTo("https://api.weather.gov/alerts/urn:oid:fixture");assertThat(event.issuedAt()).isEqualTo(Instant.parse("2026-11-04T10:00:00Z"));
    }
}
