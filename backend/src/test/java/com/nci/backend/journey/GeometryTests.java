package com.nci.backend.journey;
import java.util.List;
import com.nci.backend.route.dto.*;
import com.nci.backend.route.service.*;
import com.nci.backend.hazard.service.GeoDistance;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GeometryTests {
    private final RouteGeometry geometry = new RouteGeometry(new GeoDistance());
    @Test void distanceUsesSegmentInteriorAndClampsToEndpoints() {
        var a = new RoutePoint(0.,0.); var b = new RoutePoint(0.,2.);
        assertThat(geometry.segmentDistance(a,b,1,1)).isCloseTo(111.195,within(.001));
        assertThat(geometry.segmentDistance(a,b,0,1)).isCloseTo(0,within(.000001));
        assertThat(geometry.segmentDistance(a,b,0,3)).isCloseTo(111.195,within(.001));
        assertThat(geometry.segmentDistance(a,b,0,-1)).isCloseTo(111.195,within(.001));
    }
    @Test void handlesDateLinePolesAndDuplicateVertices() {
        assertThat(geometry.segmentDistance(new RoutePoint(0.,179.),new RoutePoint(0.,-179.),0,180)).isCloseTo(0,within(.000001));
        assertThat(geometry.segmentDistance(new RoutePoint(89.,0.),new RoutePoint(89.,180.),90,0)).isCloseTo(0,within(.000001));
        assertThat(geometry.segmentDistance(new RoutePoint(0.,0.),new RoutePoint(0.,0.),0,1)).isCloseTo(111.195,within(.001));
    }
    @Test void rejectsUnboundedAndDegenerateRoutes() {
        for (var points : List.of(List.of(new RoutePoint(0.,0.),new RoutePoint(0.,0.)),
                List.of(new RoutePoint(0.,0.),new RoutePoint(0.,10.)),
                List.of(new RoutePoint(Double.NaN,0.),new RoutePoint(0.,1.))))
            assertThatThrownBy(() -> geometry.validateAndLength(new RouteRiskRequest(points,25,7,4)))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(geometry.validateAndLength(new RouteRiskRequest(List.of(new RoutePoint(0.,0.),new RoutePoint(0.,1.)),25,7,4)))
                .isCloseTo(111.195,within(.001));
    }
}
