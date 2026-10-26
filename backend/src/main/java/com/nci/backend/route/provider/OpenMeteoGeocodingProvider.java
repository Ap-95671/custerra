package com.nci.backend.route.provider;

import java.time.Clock;
import java.util.*;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.ProviderMetadata;
import com.nci.backend.route.dto.RoutePoint;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenMeteoGeocodingProvider implements GeocodingProvider {
    private final PlanningProperties properties; private final RestClient client;
    private final ObjectMapper json; private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public OpenMeteoGeocodingProvider(PlanningProperties properties, ObjectMapper json, Clock clock) {
        this(properties,json,clock,ProviderHttp.client(properties.connectTimeoutMs(),properties.readTimeoutMs()));
    }
    OpenMeteoGeocodingProvider(PlanningProperties properties,ObjectMapper json,Clock clock,RestClient client) {
        this.properties=properties;this.json=json;this.clock=clock;this.client=client;
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    record Payload(List<Candidate> results, Boolean error, Double generationtime_ms) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    record Candidate(Long id, String name, String admin1, String country, Double latitude, Double longitude) {}
    @Override public Places search(String query) {
        var uri=UriComponentsBuilder.fromUri(properties.geocodingUrl()).queryParam("name","{name}")
                .queryParam("count",5).queryParam("language","en").queryParam("format","json").encode().buildAndExpand(query).toUri();
        var bytes=ProviderHttp.get(client,uri,"application/json",100_000);
        try {
            var payload=json.readValue(bytes,Payload.class);
            // An empty search response contains timing metadata but no results array.
            if(payload==null || Boolean.TRUE.equals(payload.error()) || (payload.results()==null && payload.generationtime_ms()==null))
                throw new IllegalArgumentException("Invalid search response");
            var candidates=new ArrayList<Place>();
            for(var c:payload.results()==null?List.<Candidate>of():payload.results()) {
                if(c==null || c.id()==null || c.name()==null || c.name().isBlank() || c.name().length()>500
                        || c.latitude()==null || c.longitude()==null || !Double.isFinite(c.latitude()) || !Double.isFinite(c.longitude())
                        || Math.abs(c.latitude())>90 || Math.abs(c.longitude())>180) throw new IllegalArgumentException("Invalid place");
                candidates.add(new Place(c.id().toString(),c.name(),c.admin1(),c.country(),new RoutePoint(c.latitude(),c.longitude())));
            }
            if(candidates.size()>5) throw new IllegalArgumentException("Too many places");
            return new Places(candidates,new ProviderMetadata("OPEN_METEO_GEOCODING","Open-Meteo / GeoNames",
                    "https://open-meteo.com/en/docs/geocoding-api","https://open-meteo.com/en/terms",
                    "User-submitted city/place search; cached locally for up to 24 hours."),clock.instant());
        } catch(RuntimeException e) { throw new ProviderException(false); }
    }
}
