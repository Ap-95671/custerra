package com.nci.backend.weather.provider;
import java.time.*;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import com.nci.backend.weather.model.WeatherQuery;
import static org.assertj.core.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="CUSTERRA_WEATHER_LIVE_AUTHORIZED",matches="true")
class WeatherLiveTests {
    @Test void boundedCurrentWeatherHasValidatedUnitsAndTimes(){
        var provider=new OpenMeteoProvider(new OpenMeteoProperties(URI.create("https://api.open-meteo.com/v1/forecast"),3000,10000),new OpenMeteoMapper(),new JsonMapper(),Clock.systemUTC());
        var result=provider.fetch(new WeatherQuery(37.7749,-122.4194));
        assertThat(result.informationType()).isEqualTo("MODELED_WEATHER");assertThat(result.validAt()).isBeforeOrEqualTo(result.fetchedAt().plusSeconds(900));
        assertThat(result.temperatureC()).isNotNull();assertThat(result.windSpeedKmh()).isNotNull();
        System.out.println("Open-Meteo bounded live parse: validAt="+result.validAt()+", retrieved="+result.fetchedAt()+", partial="+result.partial()+", stale="+result.stale()+", intervalSeconds="+result.intervalSeconds());
    }
}
