package com.nci.backend.intelligence;

import com.nci.backend.provider.ProviderException;
import com.nci.backend.hazard.service.GeoDistance;
import com.nci.backend.provider.ProviderHttp;
import com.nci.backend.route.dto.RoutePoint;
import com.nci.backend.route.service.RouteGeometry;
import com.nci.backend.route.service.RouteSegmentIndex;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class RoadClosures {
    static final URI DISTRICT_FEED = URI.create("https://cwwp2.dot.ca.gov/data/d4/lcs/lcsStatusD04.json");
    private static final String QUICK_MAP = "https://quickmap.dot.ca.gov/";
    private static final double MAX_DISTANCE_KM = 0.5;
    private static final int MAX_ROWS = 3_000;
    private static final int MAX_RETURNED = 30;
    private static final int MAX_SEGMENT_CHECKS = 5_000_000;
    private static final Duration MAX_AGE = Duration.ofMinutes(30);
    // Conservative request gate around District 4, not an administrative boundary.
    private static final Envelope BAY_AREA_GATE = new Envelope(36.8, 39.1, -124.0, -120.8);

    public record Match(String id, String reportedRoad, String direction, String facility, String type, String work,
                        String state, Instant scheduledStart, Instant scheduledEnd, Instant sourceUpdatedAt,
                        String endpoint, String endpointName, int segmentIndex, double distanceKm) {}
    public record Report(String coverage, Instant retrievedAt, Instant sourceUpdatedAt, int rejectedRecords,
                         boolean truncated, List<Match> items, String sourceUrl) {}
    record Closure(String id, String direction, String facility, String type, String work,
                   Instant observedAt, Instant scheduledStart, Instant scheduledEnd,
                   boolean started, Point begin, Point end) {}
    record Point(RoutePoint location, String road, String name) {}
    record Snapshot(Instant retrievedAt, Instant publishedAt, int rejected, List<Closure> closures) {}
    private record Version(JsonNode lcs, Instant observedAt, boolean withdrawn) {}

    private final ObjectMapper json;
    private final RouteGeometry geometry;
    private final Clock clock;
    private final RestClient client;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile Snapshot snapshot;
    private Instant nextAttempt = Instant.MIN;

    @Autowired
    public RoadClosures(ObjectMapper json, RouteGeometry geometry, Clock clock) {
        this(json, geometry, clock, ProviderHttp.client(3_000, 10_000));
    }

    RoadClosures(ObjectMapper json, RouteGeometry geometry, Clock clock, RestClient client) {
        this.json = json;
        this.geometry = geometry;
        this.clock = clock;
        this.client = client;
    }

    public Report near(List<RoutePoint> points) {
        geometry.validateAndLength(points, 20_000);
        var envelope = Envelope.cover(points);
        if (!envelope.overlaps(BAY_AREA_GATE)) return report("OUTSIDE_SCOPE", null, false, List.of());
        var source = fetch();
        if (source == null || source.publishedAt() == null) return report("UNAVAILABLE", source, false, List.of());
        var now = clock.instant();
        if (source.publishedAt().isBefore(now.minus(MAX_AGE))) return report("STALE", source, false, List.of());

        var index = new RouteSegmentIndex(List.copyOf(points), geometry);
        var matches = new ArrayList<Match>();
        int work = 0;
        boolean truncated = false;
        var candidates = envelope;
        for (var closure : source.closures()) {
            if (closure.observedAt().isBefore(now.minus(MAX_AGE))) continue;
            if (!closure.started() && closure.scheduledEnd() != null && !closure.scheduledEnd().isAfter(now)) continue;
            boolean nearBegin = candidates.contains(closure.begin().location());
            boolean nearEnd = candidates.contains(closure.end().location());
            int cost = (points.size() - 1) * ((nearBegin ? 1 : 0) + (nearEnd ? 1 : 0));
            if (work + cost > MAX_SEGMENT_CHECKS) {
                truncated = true;
                break;
            }
            work += cost;
            var begin = nearBegin ? match(closure, closure.begin(), "BEGIN", index, now) : null;
            var end = nearEnd ? match(closure, closure.end(), "END", index, now) : null;
            if (begin != null && (end == null || begin.distanceKm() <= end.distanceKm())) matches.add(begin);
            else if (end != null) matches.add(end);
        }
        matches.sort(Comparator.comparing((Match match) -> !match.state().equals("ACTIVE")).thenComparingDouble(Match::distanceKm));
        return report("PARTIAL", source, truncated || matches.size() > MAX_RETURNED, matches.stream().limit(MAX_RETURNED).toList());
    }

    private Match match(Closure closure, Point point, String endpoint, RouteSegmentIndex index, Instant now) {
        var nearest = index.closest(point.location().latitude(), point.location().longitude());
        if (nearest.distanceKm() > MAX_DISTANCE_KM) return null;
        String state = closure.started() ? "ACTIVE" : closure.scheduledStart().isAfter(now) ? "SCHEDULED" : "UNCONFIRMED";
        return new Match(closure.id(), point.road(), closure.direction(), closure.facility(), closure.type(), closure.work(),
                state, closure.scheduledStart(), closure.scheduledEnd(), closure.observedAt(),
                endpoint, point.name(), nearest.segmentIndex(), nearest.distanceKm());
    }

    private Snapshot fetch() {
        var current = snapshot;
        var now = clock.instant();
        if (current != null && now.isBefore(current.retrievedAt().plusSeconds(300))) return current;
        if (!refreshLock.tryLock()) return null;
        try {
            if (now.isBefore(nextAttempt)) return null;
            nextAttempt = now.plusSeconds(60);
            snapshot = parse(json.readTree(ProviderHttp.get(client, DISTRICT_FEED, "application/json", 6_291_456)), clock.instant());
            return snapshot;
        } catch (ProviderException | JacksonException unavailable) {
            return null;
        } finally {
            refreshLock.unlock();
        }
    }

    static Snapshot parse(JsonNode root, Instant retrievedAt) {
        var rows = root.path("data");
        if (!rows.isArray() || rows.size() > MAX_ROWS) throw new ProviderException(false);
        var byId = new HashMap<String, Version>();
        Instant publishedAt = null;
        int rejected = 0;
        for (var row : rows) {
            try {
                var lcs = row.path("lcs");
                var observedAt = epoch(lcs.path("recordTimestamp"), "recordEpoch");
                if (observedAt.isAfter(retrievedAt.plusSeconds(300))) throw new IllegalArgumentException();
                if (publishedAt == null || observedAt.isAfter(publishedAt)) publishedAt = observedAt;
                String id = IntelligenceFeed.required(lcs, "index", 80);
                var closure = lcs.path("closure");
                boolean withdrawn = flag(closure.path("code1098"), "isCode1098") || flag(closure.path("code1022"), "isCode1022");
                byId.merge(id, new Version(lcs, observedAt, withdrawn), (old, candidate) ->
                        candidate.observedAt().isAfter(old.observedAt())
                                || (candidate.observedAt().equals(old.observedAt()) && candidate.withdrawn()) ? candidate : old);
            } catch (IllegalArgumentException | java.time.DateTimeException malformed) {
                rejected++;
            }
        }
        var closures = new ArrayList<Closure>();
        for (var entry : byId.entrySet()) {
            var version = entry.getValue();
            if (version.withdrawn()) continue;
            try {
                var closure = version.lcs().path("closure");
                var time = closure.path("closureTimestamp");
                var scheduledStart = epoch(time, "closureStartEpoch");
                var scheduledEnd = flag(time, "isClosureEndIndefinite") ? null : epoch(time, "closureEndEpoch");
                if (scheduledEnd != null && scheduledEnd.isBefore(scheduledStart)) throw new IllegalArgumentException();
                var location = version.lcs().path("location");
                closures.add(new Closure(entry.getKey(), IntelligenceFeed.text(location, "travelFlowDirection", 80),
                        IntelligenceFeed.required(closure, "facility", 100), IntelligenceFeed.required(closure, "typeOfClosure", 100),
                        IntelligenceFeed.text(closure, "typeOfWork", 180), version.observedAt(), scheduledStart, scheduledEnd,
                        flag(closure.path("code1097"), "isCode1097"), point(location, "begin"), point(location, "end")));
            } catch (IllegalArgumentException | java.time.DateTimeException malformed) {
                rejected++;
            }
        }
        closures.sort(Comparator.comparing(Closure::started).reversed().thenComparing(Closure::scheduledStart).thenComparing(Closure::id));
        return new Snapshot(retrievedAt, publishedAt, rejected, List.copyOf(closures));
    }

    private static Point point(JsonNode location, String prefix) {
        var data = location.path(prefix);
        double latitude = Double.parseDouble(data.path(prefix + "Latitude").asText());
        double longitude = Double.parseDouble(data.path(prefix + "Longitude").asText());
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
            throw new IllegalArgumentException();
        return new Point(new RoutePoint(latitude, longitude), IntelligenceFeed.required(data, prefix + "Route", 40),
                IntelligenceFeed.text(data, prefix + "LocationName", 160));
    }

    private static boolean flag(JsonNode data, String field) {
        return switch (data.path(field).asText()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException();
        };
    }

    private static Instant epoch(JsonNode data, String field) {
        var seconds = Long.parseLong(data.path(field).asText());
        if (seconds <= 0) throw new IllegalArgumentException();
        return Instant.ofEpochSecond(seconds);
    }

    private Report report(String coverage, Snapshot source, boolean truncated, List<Match> items) {
        return new Report(coverage, source == null ? null : source.retrievedAt(), source == null ? null : source.publishedAt(),
                source == null ? 0 : source.rejected(), truncated, items, QUICK_MAP);
    }

    private record Envelope(double minLat, double maxLat, double minLon, double maxLon) {
        static Envelope cover(List<RoutePoint> points) {
            double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
            double longestSegmentKm = 0;
            var distances = new GeoDistance();
            RoutePoint previous = null;
            for (var point : points) {
                minLat = Math.min(minLat, point.latitude()); maxLat = Math.max(maxLat, point.latitude());
                minLon = Math.min(minLon, point.longitude()); maxLon = Math.max(maxLon, point.longitude());
                if (previous != null) longestSegmentKm = Math.max(longestSegmentKm,
                        distances.kilometers(previous.latitude(), previous.longitude(), point.latitude(), point.longitude()));
                previous = point;
            }
            // Leave a conservative margin for great-circle segments and the 500 m search around District 4.
            return new Envelope(minLat, maxLat, minLon, maxLon).padded(longestSegmentKm / 40 + 0.012);
        }
        boolean overlaps(Envelope other) {
            return minLat <= other.maxLat && maxLat >= other.minLat && minLon <= other.maxLon && maxLon >= other.minLon;
        }
        boolean contains(RoutePoint point) {
            return point.latitude() >= minLat && point.latitude() <= maxLat && point.longitude() >= minLon && point.longitude() <= maxLon;
        }
        Envelope padded(double degrees) {
            return new Envelope(minLat - degrees, maxLat + degrees, minLon - degrees, maxLon + degrees);
        }
    }
}
