package com.nci.backend.provider.usgs;

import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@Component
public class UsgsEarthquakeProvider implements HazardProvider, com.nci.backend.history.HistoricalProvider {
    public static final int MAX_EVENTS = 200;
    private static final int MAX_BYTES = 2_000_000;
    private static final Logger log = LoggerFactory.getLogger(UsgsEarthquakeProvider.class);
    private final UsgsProperties properties;
    private final UsgsMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final RestClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public UsgsEarthquakeProvider(UsgsProperties properties, UsgsMapper mapper, ObjectMapper json, Clock clock) {
        this(properties, mapper, json, clock, createClient(properties));
    }

    UsgsEarthquakeProvider(UsgsProperties properties, UsgsMapper mapper, ObjectMapper json, Clock clock,
                          RestClient client) {
        this.properties = properties;
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
        this.client = client;
    }

    private static RestClient createClient(UsgsProperties properties) {
        return com.nci.backend.provider.ProviderHttp.client(properties.connectTimeoutMs(), properties.readTimeoutMs());
    }

    @Override
    public String source() { return "USGS"; }

    @Override
    public ProviderMetadata metadata() {
        return new ProviderMetadata("USGS", "U.S. Geological Survey",
                "https://earthquake.usgs.gov/fdsnws/event/1/",
                "https://www.usgs.gov/information-policies-and-instructions/copyrights-and-credits",
                "On-demand catalog query; observations may be revised. No scheduled refresh.");
    }

    @Override
    public ProviderResult fetch(HazardQuery query) {
        return fetchCatalog(query.latitude(), query.longitude(), query.radiusKm(), query.from(), query.to(), null, MAX_EVENTS);
    }

    @Override
    public ProviderResult fetchHistory(com.nci.backend.history.HistoryQuery query) {
        query.validate(clock.instant());
        return fetchCatalog(query.latitude(), query.longitude(), query.radiusKm(), query.from(), query.to(), query.minMagnitude(), 2000);
    }

    private ProviderResult fetchCatalog(double latitude, double longitude, double radiusKm,
            java.time.Instant from, java.time.Instant to, Double minimumMagnitude, int limit) {
        var builder = UriComponentsBuilder.fromUri(properties.queryUrl())
                .queryParam("format", "geojson").queryParam("eventtype", "earthquake")
                .queryParam("latitude", latitude).queryParam("longitude", longitude)
                .queryParam("maxradiuskm", radiusKm).queryParam("starttime", from)
                .queryParam("endtime", to).queryParam("orderby", "time")
                .queryParam("limit", limit + 1).queryParam("nodata", 204);
        if (minimumMagnitude != null) builder.queryParam("minmagnitude", minimumMagnitude);
        var uri = builder.build().encode().toUri();
        try {
            byte[] bytes = client.get().uri(uri).header("Accept", "application/geo+json, application/json")
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 204) return null;
                        if (response.getStatusCode().value() != 200) throw new ProviderException(false);
                        byte[] body = response.getBody().readNBytes(MAX_BYTES + 1);
                        if (body.length > MAX_BYTES) throw new ProviderException(false);
                        return body;
                    });
            if (bytes == null) return new ProviderResult(java.util.List.of(), false);
            var feed = json.readValue(bytes, UsgsGeoJson.class);
            if (feed == null || !"FeatureCollection".equals(feed.type()) || feed.features() == null
                    || feed.features().size() > limit + 1) throw new ProviderException(false);
            var events = new LinkedHashMap<String, NormalizedHazardEvent>();
            var observedAt = clock.instant();
            for (var feature : feed.features()) {
                var event = mapper.map(feature, observedAt);
                if (event != null) events.merge(event.externalId(), event,
                        (old, next) -> next.sourceUpdatedAt().isAfter(old.sourceUpdatedAt()) ? next : old);
            }
            return new ProviderResult(events.values().stream().limit(limit).toList(),
                    feed.features().size() > limit);
        } catch (ProviderException failure) {
            log.warn("Hazard provider request failed: source=USGS status={}", failure.getStatusCode().value());
            throw failure;
        } catch (ResourceAccessException failure) {
            boolean timeout = failure.getMostSpecificCause() instanceof SocketTimeoutException;
            log.warn("Hazard provider transport failure: source=USGS timeout={}", timeout);
            throw new ProviderException(timeout);
        } catch (RuntimeException failure) {
            log.warn("Hazard provider response rejected: source=USGS type={}", failure.getClass().getSimpleName());
            throw new ProviderException(false);
        }
    }
}
