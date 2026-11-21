package com.nci.backend.route.provider;

import java.time.Clock;
import java.util.*;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.ProviderMetadata;
import com.nci.backend.route.dto.RoutePoint;
import com.nci.backend.hazard.service.GeoDistance;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

@Component
public class OsrmRouteProvider implements RouteProvider {
    private final PlanningProperties properties; private final RestClient client;
    private final ObjectMapper json; private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public OsrmRouteProvider(PlanningProperties properties,ObjectMapper json,Clock clock) {
        this(properties,json,clock,ProviderHttp.client(properties.connectTimeoutMs(),properties.readTimeoutMs()).mutate()
                .defaultHeader("User-Agent","Custerra/0.1 (local research prototype)").build());
    }
    OsrmRouteProvider(PlanningProperties properties,ObjectMapper json,Clock clock,RestClient client) {
        this.properties=properties;this.json=json;this.clock=clock;this.client=client;
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    record Payload(String code,List<Path> routes) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    record Path(String geometry,Double distance,Double duration) {}
    @Override public RoutePlan route(RoutePoint origin,RoutePoint destination,TravelMode mode) { return fetch(origin,destination,mode,false).getFirst(); }
    @Override public List<RoutePlan> alternatives(RoutePoint origin,RoutePoint destination,TravelMode mode) { return fetch(origin,destination,mode,true); }
    private List<RoutePlan> fetch(RoutePoint origin,RoutePoint destination,TravelMode mode,boolean alternatives) {
        if(mode!=TravelMode.DRIVING)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var uri=UriComponentsBuilder.fromUri(properties.routingUrl()).pathSegment(origin.longitude()+","+origin.latitude()+";"+destination.longitude()+","+destination.latitude())
                .queryParam("overview","full").queryParam("geometries","polyline6").queryParam("alternatives",alternatives).queryParam("steps",false).build().encode().toUri();
        var bytes=ProviderHttp.get(client,uri,"application/json",3_000_000);
        try {
            var payload=json.readValue(bytes,Payload.class);
            if(payload!=null&&"NoRoute".equals(payload.code()))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT);
            if(payload==null||!"Ok".equals(payload.code())||payload.routes()==null||payload.routes().isEmpty()||payload.routes().size()>(alternatives?3:1))throw new IllegalArgumentException("Invalid route response");
            var results=new ArrayList<RoutePlan>();var geometries=new HashSet<String>();
            for(var path:payload.routes()){
                if(path.distance()==null||path.duration()==null||!Double.isFinite(path.distance())||!Double.isFinite(path.duration())||path.distance()<1||path.duration()<0)throw new IllegalArgumentException("Invalid route metadata");
                if(path.distance()>900_000)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT);
                var points=EncodedRoute.decode(path.geometry());var geo=new GeoDistance();var first=points.getFirst();var last=points.getLast();
                if(geo.kilometers(origin.latitude(),origin.longitude(),first.latitude(),first.longitude())>5||geo.kilometers(destination.latitude(),destination.longitude(),last.latitude(),last.longitude())>5)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT);
                if(geometries.add(path.geometry()))results.add(new RoutePlan(path.geometry(),6,points,path.distance()/1000,path.duration(),new ProviderMetadata("OSRM","OSRM / OpenStreetMap contributors","https://project-osrm.org/docs/v5.24.0/api/","https://routing.openstreetmap.de/about.html","On-demand driving geometry; duration excludes hazard impacts and live traffic."),clock.instant()));
            }
            return List.copyOf(results);
        }catch(ResponseStatusException e){throw e;}catch(RuntimeException e){throw new ProviderException(false);}
    }
}
