package com.nci.backend.config;

import com.nci.backend.provider.usgs.UsgsProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({UsgsProperties.class, com.nci.backend.route.provider.PlanningProperties.class, com.nci.backend.provider.firms.FirmsProperties.class, com.nci.backend.provider.gdacs.GdacsProperties.class, com.nci.backend.weather.provider.OpenMeteoProperties.class})
public class ProviderConfig {
    @Bean
    public Clock clock() { return Clock.systemUTC(); }
}
