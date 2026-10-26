package com.nci.backend.journey;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.nci.backend.hazard.service.*;
import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.provider.model.HazardQuery;
import com.nci.backend.route.service.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static com.nci.backend.journey.FeatureFixtures.*;
class RouteRiskTests {
    @Test void boundsOneQueryThenFiltersBySegmentAndMagnitudeWithoutRiskClaims() {
        var nearby = mock(NearbyHazardService.class);
        var items = List.of(new NearbyHazardResponse.Item(event("inside",0,1,4,NOW),111),
                new NearbyHazardResponse.Item(event("far",1,1,5,NOW),150),
                new NearbyHazardResponse.Item(event("small",0,1,3.9,NOW),111));
        when(nearby.earthquakes(anyDouble(),anyDouble(),anyDouble(),anyInt())).thenReturn(new NearbyHazardResponse(
                new HazardQuery(0,0,248,NOW.minusSeconds(86400),NOW),NOW,items,true,List.of(),"Test"));
        var result = new RouteRiskService(new RouteGeometry(new GeoDistance()),nearby).assess(ROUTE);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().getFirst().event().externalId()).isEqualTo("inside");
        assertThat(result.truncated()).isTrue(); assertThat(result.overallRisk()).isEqualTo("UNKNOWN");
        verify(nearby).earthquakes(eq(0.),eq(0.),doubleThat(radius -> Math.abs(radius-247.39016)<.001),eq(7));
    }
    @Test void monitoringMatchesOtherHazardsWithoutEarthquakeMagnitudeAndPreservesSegmentAndCoverage() {
        var nearby = mock(NearbyHazardService.class);
        var thermal = new com.nci.backend.provider.model.NormalizedHazardEvent("thermal",com.nci.backend.hazard.model.HazardType.WILDFIRE,
                "Thermal anomaly",com.nci.backend.hazard.model.SeverityLevel.UNKNOWN,com.nci.backend.hazard.model.InformationType.OBSERVED_EVENT,
                0,1,null,NOW,null,"FIRMS","https://firms.modaps.eosdis.nasa.gov/",NOW,NOW,
                com.nci.backend.hazard.model.ConfidenceLevel.HIGH,java.util.Map.of("frp",10.));
        when(nearby.nearby(anyDouble(),anyDouble(),anyDouble(),anyInt())).thenReturn(new NearbyHazardResponse(
                new HazardQuery(0,0,248,NOW.minusSeconds(86400),NOW),NOW,
                List.of(new NearbyHazardResponse.Item(thermal,111),new NearbyHazardResponse.Item(event("small",0,1,2,NOW),111)),false,List.of(),"Test"));
        var result = new RouteRiskService(new RouteGeometry(new GeoDistance()),nearby).assessMonitored(ROUTE);
        assertThat(result.matches()).singleElement().satisfies(m -> { assertThat(m.event().source()).isEqualTo("FIRMS");assertThat(m.segmentIndex()).isZero(); });
        verify(nearby,never()).earthquakes(anyDouble(),anyDouble(),anyDouble(),anyInt());
    }

}
