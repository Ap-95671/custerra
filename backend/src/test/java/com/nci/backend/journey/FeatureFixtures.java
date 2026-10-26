package com.nci.backend.journey;
import java.time.Instant;
import java.util.*;
import com.nci.backend.provider.model.*;
import com.nci.backend.hazard.model.*;
import com.nci.backend.route.dto.*;
public class FeatureFixtures {
    public static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    public static final RouteRiskRequest ROUTE = new RouteRiskRequest(List.of(new RoutePoint(0.,0.),new RoutePoint(0.,2.)),25,7,4);
    public static NormalizedHazardEvent event(String id, double lat, double lon, double magnitude, Instant time) {
        return new NormalizedHazardEvent(id,HazardType.EARTHQUAKE,"Test fixture",SeverityLevel.UNKNOWN,
                InformationType.OBSERVED_EVENT,lat,lon,null,time,null,"USGS","https://earthquake.usgs.gov/earthquakes/eventpage/"+id,
                NOW,NOW,ConfidenceLevel.UNKNOWN,Map.of("magnitude",magnitude));
    }
    public static RouteRiskResponse assessment(boolean truncated, String... ids) {
        return new RouteRiskResponse("route-proximity-v1",ROUTE,NOW,222.39,"UNKNOWN",truncated,
                Arrays.stream(ids).map(id -> new RouteRiskResponse.Match(event(id,0,1,4,NOW.minusSeconds(3600)),0,0)).toList(),List.of(),"Test fixture");
    }
}
