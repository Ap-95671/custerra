package com.nci.backend.weather.provider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoPayload(Double latitude, Double longitude, Integer utc_offset_seconds,
        Map<String, String> current_units, Current current) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Current(Long time, Integer interval, Double temperature_2m, Double rain,
                          Double precipitation, Double wind_speed_10m) {}
}
