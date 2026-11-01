package com.nci.backend.route.service;
import java.util.*;
import org.springframework.stereotype.Service;
import com.nci.backend.route.dto.*;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.hazard.model.HazardType;
@Service
public class RouteRiskService {
    private final RouteGeometry geometry;
    private final NearbyHazardService nearby;
    public RouteRiskService(RouteGeometry geometry, NearbyHazardService nearby) { this.geometry = geometry; this.nearby = nearby; }
    public RouteRiskResponse assess(RouteRiskRequest request) {
        return assess(request, geometry.validateAndLength(request), false);
    }
    public RouteRiskResponse assessGenerated(RouteRiskRequest request) {
        return assess(request, geometry.validateAndLength(request,20_000), false);
    }
    public RouteRiskResponse assessMonitored(RouteRiskRequest request) {
        return assess(request, geometry.validateAndLength(request), true);
    }
    public RouteRiskResponse assessMonitoredGenerated(RouteRiskRequest request) {
        return assess(request,geometry.validateAndLength(request,20_000),true);
    }
    private RouteRiskResponse assess(RouteRiskRequest request, double length, boolean multiHazard) {
        var origin = request.points().getFirst();
        // Total path length plus corridor encloses every segment and its corridor by triangle inequality.
        double radius = Math.min(1000, length + request.corridorKm());
        var result = multiHazard ? nearby.nearby(origin.latitude(), origin.longitude(), radius, request.days())
                : nearby.earthquakes(origin.latitude(), origin.longitude(), radius, request.days());
        var matches = new ArrayList<RouteRiskResponse.Match>();
        var index=new RouteSegmentIndex(request.points(),geometry);
        for (var item : result.items()) {
            var event = item.event();
            if ((!multiHazard || (event.source().equals("USGS") && event.hazardType() == HazardType.EARTHQUAKE))
                    && event.measurements().getOrDefault("magnitude", Double.NEGATIVE_INFINITY) < request.minMagnitude()) continue;
            var nearest=index.closest(event.latitude(),event.longitude());
            double closest=nearest.distanceKm();int segment=nearest.segmentIndex();
            if (closest <= request.corridorKm()) matches.add(new RouteRiskResponse.Match(event, closest, segment));
        }
        matches.sort(Comparator.comparingDouble(RouteRiskResponse.Match::distanceKm)
                .thenComparing(m -> m.event().source()).thenComparing(m -> m.event().externalId()));
        return new RouteRiskResponse(multiHazard ? "route-proximity-v2-multi-hazard" : "route-proximity-v1", request, result.fetchedAt(), length, "UNKNOWN", result.truncated(),
                matches, result.sources(), (multiHazard ? "Multi-hazard reported point proximity to stored route segments. Magnitude filters apply only to USGS earthquakes. " : "Earthquake epicenter proximity to supplied great-circle segments only. ")
                + "No road navigation, arrival-time forecast, impact radius or safe-route recommendation. "
                + "A truncated enclosing query may omit route events; empty results do not establish safety.", result.coverageStatus(), result.providerStatuses());
    }
}
