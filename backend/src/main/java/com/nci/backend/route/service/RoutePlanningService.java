package com.nci.backend.route.service;

import java.time.*;
import java.util.*;
import com.nci.backend.route.provider.*;
import com.nci.backend.route.dto.*;
import com.nci.backend.provider.ProviderException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoutePlanningService {
    private final GeocodingProvider geocoder;
    private final RouteProvider router;
    private final RouteRiskService risk;
    private final Clock clock;
    private final LinkedHashMap<String,GeocodingProvider.Places> places=new LinkedHashMap<>();
    private Instant nextSearch=Instant.MIN, nextRoute=Instant.MIN;
    public RoutePlanningService(GeocodingProvider geocoder,RouteProvider router,RouteRiskService risk,Clock clock) {
        this.geocoder=geocoder;this.router=router;this.risk=risk;this.clock=clock;
    }
    public synchronized GeocodingProvider.Places search(String query) {
        if(query==null || query.strip().length()<2 || query.length()>120 || query.chars().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String key=query.strip().toLowerCase(Locale.ROOT);
        var now=clock.instant(); var cached=places.get(key);
        if(cached!=null && cached.retrievedAt().isAfter(now.minus(Duration.ofDays(1)))) return cached;
        if(now.isBefore(nextSearch)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        nextSearch=now.plusSeconds(1);
        var result=geocoder.search(query.strip());
        places.put(key,result);
        if(places.size()>200) places.remove(places.keySet().iterator().next());
        return result;
    }
    private synchronized void reserveRoute() {
        var now=clock.instant();
        if(now.isBefore(nextRoute)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        nextRoute=now.plusSeconds(1);
    }
    public PlannedRouteResponse plan(RoutePlanRequest request) {
        reserveRoute();
        var route=router.route(request.origin(),request.destination(),request.mode());
        PlannedRouteResponse.Exposure exposure=null; String error=null;
        try {
            var result=risk.assessGenerated(new RouteRiskRequest(route.points(),request.corridorKm(),request.days(),request.minMagnitude()));
            exposure=new PlannedRouteResponse.Exposure(result.modelVersion(),result.assessedAt(),result.overallRisk(),result.truncated(),
                    result.matches(),result.sources(),"USGS earthquake epicenter proximity to the retrieved driving geometry. "
                    + "No forecast of road closures, arrival-time conditions or safe travel. "
                    + (result.truncated()?"Incomplete source results may omit matches.":result.matches().isEmpty()
                    ?"No currently retrieved monitored earthquakes intersect the route within the configured corridor and magnitude filter.":"Matching evidence is listed by affected geometry segment."));
        } catch(ProviderException e) { error=e.getStatusCode().value()==504?"HAZARD_PROVIDER_TIMEOUT":"HAZARD_PROVIDER_UNAVAILABLE"; }
        var points=route.points();
        var bounds=new PlannedRouteResponse.Bounds(points.stream().mapToDouble(RoutePoint::latitude).min().orElseThrow(),
                points.stream().mapToDouble(RoutePoint::longitude).min().orElseThrow(),points.stream().mapToDouble(RoutePoint::latitude).max().orElseThrow(),
                points.stream().mapToDouble(RoutePoint::longitude).max().orElseThrow());
        return new PlannedRouteResponse(request,route.geometry(),route.precision(),route.distanceKm(),route.durationSeconds(),bounds,
                points.size()-1,route.source(),route.retrievedAt(),exposure,error,List.of("Driving only; routes over 900 km are not supported yet.",
                "Duration excludes live traffic and hazard impacts. Endpoints may snap up to 5 km to the road network.",
                "Earthquake exposure only in this milestone. Other hazard families remain unassessed.",
                "Saving this geometry creates a planned journey. Monitoring requires explicit activation and an enabled worker."));
    }
}
