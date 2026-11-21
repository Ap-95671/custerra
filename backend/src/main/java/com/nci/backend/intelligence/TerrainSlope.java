package com.nci.backend.intelligence;

import tools.jackson.databind.JsonNode;

/** Center differences on a small DEM stencil. This estimates broad modeled relief. */
final class TerrainSlope {
    private static final int OFFSET_METERS = 225;
    private static final double METERS_PER_LATITUDE_DEGREE = Math.PI * 6_371_008.8 / 180;

    record Sample(String latitudes, String longitudes, int size) {}
    record Estimate(double degrees, int offsetMeters) {}

    private TerrainSlope() {}

    static Sample sample(double latitude, double longitude, boolean includeSlope) {
        if (!includeSlope || Math.abs(latitude) > 80)
            return new Sample(String.valueOf(latitude), String.valueOf(longitude), 1);
        double latitudeOffset = OFFSET_METERS / METERS_PER_LATITUDE_DEGREE;
        double longitudeOffset = latitudeOffset / Math.cos(Math.toRadians(latitude));
        return new Sample(latitude + "," + (latitude + latitudeOffset) + "," + (latitude - latitudeOffset) + "," + latitude + "," + latitude,
                longitude + "," + longitude + "," + longitude + "," + wrap(longitude + longitudeOffset) + "," + wrap(longitude - longitudeOffset), 5);
    }

    static Double validElevation(JsonNode value) {
        if (value == null || !value.isNumber()) return null;
        double meters = value.asDouble();
        return Double.isFinite(meters) && meters >= -12_000 && meters <= 9_000 ? meters : null;
    }

    static Estimate estimate(JsonNode values) {
        var north = validElevation(values.get(1));
        var south = validElevation(values.get(2));
        var east = validElevation(values.get(3));
        var west = validElevation(values.get(4));
        if (north == null || south == null || east == null || west == null) return null;
        double gradient = Math.hypot(north - south, east - west) / (2 * OFFSET_METERS);
        return new Estimate(Math.toDegrees(Math.atan(gradient)), OFFSET_METERS);
    }

    private static double wrap(double longitude) {
        return (longitude + 540) % 360 - 180;
    }
}
