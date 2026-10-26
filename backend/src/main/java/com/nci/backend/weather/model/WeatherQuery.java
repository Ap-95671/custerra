package com.nci.backend.weather.model;
public record WeatherQuery(double latitude, double longitude) {
    public WeatherQuery {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("Invalid weather coordinates");
    }
}
