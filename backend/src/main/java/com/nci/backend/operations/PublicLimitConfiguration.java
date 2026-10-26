package com.nci.backend.operations;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.nci.backend.auth.SecurityErrors;
@Configuration
public class PublicLimitConfiguration {
    @Bean FilterRegistrationBean<PublicRateLimit> publicRateLimiter(Clock clock,SecurityErrors errors,@Value("${PUBLIC_REQUESTS_PER_MINUTE:120}") int limit){
        var registration=new FilterRegistrationBean<>(new PublicRateLimit(clock,errors,limit));registration.setOrder(-50);return registration;
    }
}
