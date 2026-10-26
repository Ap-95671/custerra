package com.nci.backend.hazard.service;

import org.springframework.stereotype.Component;

@Component
public class GeoDistance {
    public double kilometers(double latitude, double longitude, double otherLatitude, double otherLongitude) {
        double lat1 = Math.toRadians(latitude), lat2 = Math.toRadians(otherLatitude);
        double dLat = lat2 - lat1, dLon = Math.toRadians(otherLongitude - longitude);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.clamp(a, 0, 1)));
    }
}
