package com.nci.backend.weather.provider;
import com.nci.backend.weather.model.WeatherQuery;
import com.nci.backend.weather.dto.WeatherResponse;
public interface WeatherProvider { WeatherResponse fetch(WeatherQuery query); }
