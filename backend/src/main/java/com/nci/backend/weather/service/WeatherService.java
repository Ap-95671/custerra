package com.nci.backend.weather.service;
import com.nci.backend.weather.dto.WeatherResponse;
import com.nci.backend.weather.model.WeatherQuery;
import com.nci.backend.weather.provider.WeatherProvider;
import org.springframework.stereotype.Service;
@Service
public class WeatherService {
    private final WeatherProvider provider;
    public WeatherService(WeatherProvider provider) { this.provider = provider; }
    public WeatherResponse location(double latitude, double longitude) { return provider.fetch(new WeatherQuery(latitude, longitude)); }
}
