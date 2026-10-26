package com.nci.backend.operations;
import org.junit.jupiter.api.Test;
import java.time.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.assertj.core.api.Assertions.*;
class OperationsTests {
    @Test void rateLimiterBoundsPeersAndCounts(){var limit=new PublicRateLimit(Clock.systemUTC(),null,2);assertThat(limit.acquire("peer")).isTrue();assertThat(limit.acquire("peer")).isTrue();assertThat(limit.acquire("peer")).isFalse();assertThat(limit.acquire("another")).isTrue();}
    @Test void providerHealthPreservesUnavailableAndMetricTags(){var meters=new SimpleMeterRegistry();var t=new ProviderTelemetry(meters,Clock.systemUTC());assertThat(t.snapshot()).allMatch(s->s.state().equals("NOT_CHECKED"));t.record("FIRMS","UNAVAILABLE",100);assertThat(t.snapshot()).anyMatch(s->s.provider().equals("FIRMS")&&s.state().equals("UNAVAILABLE"));assertThat(meters.get("custerra.provider.degraded").counter().count()).isEqualTo(1);}
    @Test void productionRejectsInsecureOrIncompleteConfig(){assertThatThrownBy(()->ProductionGuard.validate("jdbc:postgresql://db/app","secret","http://localhost:3000","false","")).isInstanceOf(IllegalStateException.class);ProductionGuard.validate("jdbc:postgresql://db/app?sslmode=verify-full","secret","https://custerra.example","true","x".repeat(32));}
}
