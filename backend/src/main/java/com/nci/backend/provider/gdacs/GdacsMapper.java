package com.nci.backend.provider.gdacs;

import com.nci.backend.hazard.model.*;
import com.nci.backend.provider.model.NormalizedHazardEvent;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

@Component
public class GdacsMapper {
    static final String GDACS = "http://www.gdacs.org";
    static final String GEO = "http://www.w3.org/2003/01/geo/wgs84_pos#";
    static final Map<String, HazardType> TYPES = Map.of("EQ", HazardType.EARTHQUAKE, "TC", HazardType.CYCLONE,
            "FL", HazardType.FLOOD, "VO", HazardType.VOLCANIC_ERUPTION, "TS", HazardType.TSUNAMI, "WF", HazardType.WILDFIRE);

    public NormalizedHazardEvent map(Element item, Instant retrievedAt) {
        String type = required(item, GDACS, "eventtype"), id = required(item, GDACS, "eventid");
        var hazard = TYPES.get(type);
        if (hazard == null) throw new IllegalArgumentException("Unsupported GDACS hazard");
        if (!id.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid identity");
        double lat = Double.parseDouble(required(item, GEO, "lat")), lon = Double.parseDouble(required(item, GEO, "long"));
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
            throw new IllegalArgumentException("Invalid coordinates");
        var start = date(required(item, GDACS, "fromdate"));
        var end = optionalDate(text(item, GDACS, "todate"));
        var updated = optionalDate(text(item, GDACS, "datemodified"));
        if (end != null && end.isBefore(start)) throw new IllegalArgumentException("Invalid event interval");
        String url = required(item, null, "link");
        var uri = URI.create(url);
        if (!("www.gdacs.org".equals(uri.getHost()) || "gdacs.org".equals(uri.getHost()))
                || uri.getUserInfo() != null || uri.getPort() != -1) throw new IllegalArgumentException("Invalid evidence URL");
        var attrs = new HashMap<String, String>();
        // The current HTTPS RSS feed publishes legacy HTTP report citations.
        // Upgrade only the known report endpoint; never fetch HTTP or follow a redirect.
        if ("http".equals(uri.getScheme()) && "/report.aspx".equals(uri.getRawPath())) {
            attrs.put("originalSourceUrl", url);
            attrs.put("sourceUrlNormalization", "GDACS_REPORT_HTTP_TO_HTTPS");
            url = "https" + url.substring(4);
        } else if (!"https".equals(uri.getScheme())) throw new IllegalArgumentException("Invalid evidence URL");
        for (var key : List.of("eventtype", "eventid", "episodeid", "alertlevel", "country", "iso3", "bbox", "version", "iscurrent")) {
            var value = text(item, GDACS, key);
            if (!value.isBlank()) attrs.put(key, value);
        }
        attrs.put("areaMeaning", "GDACS bbox is a source map extent, not a validated affected-area polygon.");
        var measurements = new HashMap<String, Double>();
        String alert = text(item, GDACS, "alertscore");
        if (!alert.isBlank()) {
            double score = Double.parseDouble(alert);
            if (!Double.isFinite(score)) throw new IllegalArgumentException("Invalid alert score");
            measurements.put("providerAlertScore", score);
        }
        // Colors describe the provider's potential humanitarian impact classification, not local danger.
        var severity = switch (attrs.getOrDefault("alertlevel", "").toLowerCase(Locale.ROOT)) {
            case "green" -> SeverityLevel.LOW; case "orange" -> SeverityLevel.MODERATE;
            case "red" -> SeverityLevel.HIGH; default -> SeverityLevel.UNKNOWN;
        };
        var title = required(item, null, "title");
        if (title.length() > 500) throw new IllegalArgumentException("Invalid title");
        return new NormalizedHazardEvent(type + ":" + id, hazard, title, severity, InformationType.ADVISORY,
                lat, lon, null, start, end, "GDACS", url, start, updated, ConfidenceLevel.UNKNOWN,
                measurements, retrievedAt, attrs);
    }
    private Instant optionalDate(String value) { return value.isBlank() ? null : date(value); }
    static Instant date(String value) { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
    private String required(Element element, String ns, String tag) {
        var value = text(element, ns, tag);
        if (value.isBlank()) throw new IllegalArgumentException("Missing GDACS field");
        return value;
    }
    static String text(Element element, String ns, String tag) {
        var nodes = element.getElementsByTagNameNS(ns, tag);
        String value = nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
        if (value.length() > 2000) throw new IllegalArgumentException("Oversized GDACS field");
        return value;
    }
}
