package com.nci.backend.provider.usgs;

import com.nci.backend.hazard.model.*;
import com.nci.backend.provider.model.NormalizedHazardEvent;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import org.springframework.stereotype.Component;

@Component
public class UsgsMapper {
    public NormalizedHazardEvent map(UsgsGeoJson.Feature feature, Instant observedAt) {
        if (feature == null || !"Feature".equals(feature.type()) || feature.properties() == null
                || feature.id() == null || feature.id().isBlank() || feature.id().length() > 200) {
            throw new IllegalArgumentException("Invalid feature identity");
        }
        var p = feature.properties();
        if (p.type() == null) throw new IllegalArgumentException("Missing event classification");
        if (!"earthquake".equals(p.type())) return null;
        var geometry = feature.geometry();
        if (geometry == null || !"Point".equals(geometry.type()) || geometry.coordinates() == null
                || geometry.coordinates().size() != 3 || p.time() == null || p.updated() == null) {
            throw new IllegalArgumentException("Invalid earthquake geometry or time");
        }
        var c = geometry.coordinates();
        if (c.stream().anyMatch(v -> v == null || !Double.isFinite(v))
                || c.get(0) < -180 || c.get(0) > 180 || c.get(1) < -90 || c.get(1) > 90) {
            throw new IllegalArgumentException("Invalid earthquake coordinates");
        }
        URI evidence = URI.create(p.url() == null ? "" : p.url());
        if (!"https".equals(evidence.getScheme()) || !"earthquake.usgs.gov".equals(evidence.getHost())
                || evidence.getUserInfo() != null || p.url().length() > 2000) {
            throw new IllegalArgumentException("Invalid evidence URL");
        }
        String title = p.title() == null || p.title().isBlank() ? p.place() : p.title();
        if (title == null || title.isBlank()) title = "Earthquake reported by USGS";
        if (title.length() > 500) throw new IllegalArgumentException("Invalid title length");
        var measurements = new HashMap<String, Double>();
        measurements.put("depthKm", c.get(2));
        if (p.mag() != null) {
            if (!Double.isFinite(p.mag())) throw new IllegalArgumentException("Invalid magnitude");
            measurements.put("magnitude", p.mag());
        }
        return new NormalizedHazardEvent(feature.id(), HazardType.EARTHQUAKE, title,
                SeverityLevel.UNKNOWN, InformationType.OBSERVED_EVENT, c.get(1), c.get(0), null,
                Instant.ofEpochMilli(p.time()), null, "USGS", evidence.toString(), Instant.ofEpochMilli(p.time()),
                Instant.ofEpochMilli(p.updated()), ConfidenceLevel.UNKNOWN, measurements, observedAt, java.util.Map.of());
    }
}
