package com.nci.backend.route.service;

import com.nci.backend.route.dto.*;
import com.nci.backend.hazard.service.GeoDistance;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shortest great-circle segments on a sphere. No road routing or travel-time inference. */
@Component
public class RouteGeometry {
    private static final double EARTH_KM = 6371.0088;
    private final GeoDistance geo;
    public RouteGeometry(GeoDistance geo) { this.geo = geo; }
    public double validateAndLength(RouteRiskRequest route) { return validateAndLength(route,20); }
    public double validateAndLength(RouteRiskRequest route,int maxPoints) {
        if (route == null || route.points() == null || route.points().size() < 2 || route.points().size() > maxPoints
                || !Double.isFinite(route.corridorKm()) || route.corridorKm() < 1 || route.corridorKm() > 100
                || route.days() < 1 || route.days() > 30 || !Double.isFinite(route.minMagnitude())
                || route.minMagnitude() < 0 || route.minMagnitude() > 10) throw bad();
        for (var p : route.points()) if (p == null || p.latitude() == null || p.longitude() == null
                || !Double.isFinite(p.latitude()) || !Double.isFinite(p.longitude())
                || Math.abs(p.latitude()) > 90 || Math.abs(p.longitude()) > 180) throw bad();
        double length = 0;
        for (int i = 1; i < route.points().size(); i++) {
            var a = route.points().get(i-1); var b = route.points().get(i);
            length += geo.kilometers(a.latitude(), a.longitude(), b.latitude(), b.longitude());
        }
        if (length < 0.001 || length > 900) throw bad();
        return length;
    }
    public double segmentDistance(RoutePoint a, RoutePoint b, double latitude, double longitude) {
        double segment = geo.kilometers(a.latitude(), a.longitude(), b.latitude(), b.longitude()) / EARTH_KM;
        double start = geo.kilometers(a.latitude(), a.longitude(), latitude, longitude) / EARTH_KM;
        double end = geo.kilometers(b.latitude(), b.longitude(), latitude, longitude);
        if (segment < 1e-12) return start * EARTH_KM;
        double difference = bearing(a.latitude(), a.longitude(), latitude, longitude)
                - bearing(a.latitude(), a.longitude(), b.latitude(), b.longitude());
        double along = Math.atan2(Math.sin(start) * Math.cos(difference), Math.cos(start));
        if (along < 0 || along > segment) return Math.min(start * EARTH_KM, end);
        return Math.abs(Math.asin(Math.clamp(Math.sin(start) * Math.sin(difference), -1, 1))) * EARTH_KM;
    }
    private double bearing(double lat, double lon, double otherLat, double otherLon) {
        double a = Math.toRadians(lat), b = Math.toRadians(otherLat), d = Math.toRadians(otherLon-lon);
        return Math.atan2(Math.sin(d)*Math.cos(b), Math.cos(a)*Math.sin(b)-Math.sin(a)*Math.cos(b)*Math.cos(d));
    }
    private ResponseStatusException bad() { return new ResponseStatusException(HttpStatus.BAD_REQUEST); }
}
