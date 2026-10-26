package com.nci.backend.journey;

import com.nci.backend.route.provider.*;
import com.nci.backend.route.dto.*;
import com.nci.backend.route.service.*;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import com.nci.backend.hazard.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutePlanningTests {
    final Instant now=Instant.parse("2026-10-09T12:00:00Z");
    final ProviderMetadata source=new ProviderMetadata("test","test","https://example.test","https://example.test","test");
    final List<RoutePoint> points=List.of(new RoutePoint(0.,0.),new RoutePoint(0.,1.));
    final RoutePlanRequest request=new RoutePlanRequest(points.getFirst(),points.getLast(),RouteProvider.TravelMode.DRIVING,25,7,4);
    @Test void routesActualGeometryThroughExistingAssessmentAndReturnsCompactOutput() {
        var geocoder=mock(GeocodingProvider.class); var router=mock(RouteProvider.class); var nearby=mock(NearbyHazardService.class);
        when(router.route(any(),any(),any())).thenReturn(new RouteProvider.RoutePlan("???_c`|@",6,points,112,7200,source,now));
        when(nearby.earthquakes(anyDouble(),anyDouble(),anyDouble(),anyInt())).thenReturn(new com.nci.backend.hazard.dto.NearbyHazardResponse(
                new HazardQuery(0,0,140,now.minusSeconds(604800),now),now,List.of(),false,List.of(source),"test"));
        var service=new RoutePlanningService(geocoder,router,new RouteRiskService(new RouteGeometry(new GeoDistance()),nearby),Clock.fixed(now,ZoneOffset.UTC));
        var result=service.plan(request);
        assertThat(result.segmentCount()).isEqualTo(1);assertThat(result.assessment().overallRisk()).isEqualTo("UNKNOWN");
        assertThat(result.assessment().explanation()).contains("No currently retrieved monitored earthquakes");
        assertThat(new tools.jackson.databind.json.JsonMapper().writeValueAsString(result)).doesNotContain("\"points\"");
        verify(nearby).earthquakes(eq(0.),eq(0.),doubleThat(radius->radius>136 && radius<137),eq(7));
    }
    @Test void hazardFailurePreservesRetrievedDrivingRoute() {
        var router=mock(RouteProvider.class);var risk=mock(RouteRiskService.class);
        when(router.route(any(),any(),any())).thenReturn(new RouteProvider.RoutePlan("???_c`|@",6,points,112,7200,source,now));
        when(risk.assessGenerated(any())).thenThrow(new ProviderException(true));
        var result=new RoutePlanningService(mock(GeocodingProvider.class),router,risk,Clock.fixed(now,ZoneOffset.UTC)).plan(request);
        assertThat(result.geometry()).isEqualTo("???_c`|@");assertThat(result.assessment()).isNull();assertThat(result.assessmentError()).isEqualTo("HAZARD_PROVIDER_TIMEOUT");
    }
    @Test void placeCacheAndGlobalThrottleProtectProviders() {
        var geocoder=mock(GeocodingProvider.class);
        var result=new GeocodingProvider.Places(List.of(),source,now);
        when(geocoder.search("Delhi")).thenReturn(result);
        var service=new RoutePlanningService(geocoder,mock(RouteProvider.class),mock(RouteRiskService.class),Clock.fixed(now,ZoneOffset.UTC));
        assertThat(service.search("Delhi")).isEqualTo(result);assertThat(service.search(" delhi ")).isEqualTo(result);
        verify(geocoder,times(1)).search(any());
        assertThatThrownBy(()->service.search("Manali")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(429));
        assertThatThrownBy(()->service.search("\nDelhi")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void manualBoundsStayIntactAndGeneratedGeometryUsesAllVertices() {
        var points=new ArrayList<RoutePoint>();for(int i=0;i<25;i++)points.add(new RoutePoint(0.,i*.001));
        var input=new RouteRiskRequest(points,25,7,4);var geometry=new RouteGeometry(new GeoDistance());
        assertThatThrownBy(()->geometry.validateAndLength(input)).isInstanceOf(ResponseStatusException.class);
        var nearby=mock(NearbyHazardService.class);
        when(nearby.earthquakes(anyDouble(),anyDouble(),anyDouble(),anyInt())).thenThrow(new ProviderException(false));
        assertThatThrownBy(()->new RouteRiskService(geometry,nearby).assessGenerated(input)).isInstanceOf(ProviderException.class);
    }
}
