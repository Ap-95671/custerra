package com.nci.backend.provider.usgs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsgsGeoJson(String type, List<Feature> features) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(String type, String id, Properties properties, Geometry geometry) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(String type, String title, String place, Long time, Long updated,
                             String url, Double mag) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(String type, List<Double> coordinates) {}
}
