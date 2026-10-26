package com.nci.backend.provider.firms;

import com.nci.backend.hazard.model.*;
import com.nci.backend.provider.model.NormalizedHazardEvent;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FirmsMapper {
    public NormalizedHazardEvent map(Map<String, String> row, Instant retrievedAt) {
        double lat = number(row, "latitude"), lon = number(row, "longitude");
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180) throw new IllegalArgumentException("Invalid coordinates");
        String time = required(row, "acq_time");
        if (!time.matches("[0-9]{1,4}")) throw new IllegalArgumentException("Invalid acquisition time");
        int hhmm = Integer.parseInt(time);
        var observed = LocalDate.parse(required(row, "acq_date")).atTime(hhmm / 100, hhmm % 100).toInstant(ZoneOffset.UTC);
        var attributes = new HashMap<String, String>();
        for (var name : List.of("satellite", "instrument")) attributes.put(name, required(row, name));
        for (var name : List.of("confidence", "daynight")) {
            if (row.containsKey(name) && !row.get(name).isBlank()) attributes.put(name, row.get(name));
        }
        if (!"VIIRS".equalsIgnoreCase(attributes.get("instrument"))) throw new IllegalArgumentException("Unexpected instrument");
        attributes.put("product", "VIIRS_SNPP_NRT");
        attributes.put("identityMethod", "product/satellite/instrument/acquisition/coordinates; no source event ID supplied");
        if (row.containsKey("version")) attributes.put("version", row.get("version"));
        var values = new HashMap<String, Double>();
        for (var name : List.of("bright_ti4", "bright_ti5", "frp", "scan", "track")) {
            if (row.containsKey(name) && !row.get(name).isBlank()) values.put(name, number(row, name));
        }
        // Source provides observation rows, not incident IDs. Confidence/intensity revisions do not alter identity.
        String identity = String.join("|", "VIIRS_SNPP_NRT", attributes.get("satellite"), attributes.get("instrument"),
                observed.toString(), Double.toString(lat), Double.toString(lon));
        var confidence = switch (attributes.getOrDefault("confidence", "").toLowerCase(Locale.ROOT)) {
            case "l" -> ConfidenceLevel.LOW; case "n" -> ConfidenceLevel.MEDIUM;
            case "h" -> ConfidenceLevel.HIGH; default -> ConfidenceLevel.UNKNOWN;
        };
        return new NormalizedHazardEvent(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString(),
                HazardType.WILDFIRE, "Satellite-detected thermal anomaly", SeverityLevel.UNKNOWN, InformationType.OBSERVED_EVENT,
                lat, lon, null, observed, null, "FIRMS", "https://firms.modaps.eosdis.nasa.gov/map/#d:24hrs;@"
                + lon + "," + lat + ",10z", observed, null, confidence, values, retrievedAt, attributes);
    }
    private String required(Map<String, String> row, String key) {
        var value = row.get(key);
        if (value == null || value.isBlank() || value.length() > 200) throw new IllegalArgumentException("Missing FIRMS value");
        return value;
    }
    private double number(Map<String, String> row, String key) {
        double value = Double.parseDouble(required(row, key));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid measurement");
        return value;
    }
}
