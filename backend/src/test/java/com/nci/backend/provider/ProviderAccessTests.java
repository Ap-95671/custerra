package com.nci.backend.provider;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.nci.backend.provider.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderAccessTests {
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-11-01T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
    HazardProvider provider(String source){var p=mock(HazardProvider.class);when(p.source()).thenReturn(source);return p;}
    HazardQuery query(Instant now){return new HazardQuery(0,0,100,now.minusSeconds(86400),now);}
    @Test void cachePreservesRetrievalTimestampAndExpiresWithoutStaleFallback() {
        var clock=new MutableClock();var access=new HazardProviderAccess(clock);var p=provider("GDACS");
        when(p.fetch(any())).thenReturn(new ProviderResult(List.of(),false,CoverageStatus.PARTIAL,List.of()));
        var first=access.fetch(p,query(clock.now));clock.now=clock.now.plusSeconds(30);
        var second=access.fetch(p,query(clock.now));assertThat(second.retrievedAt()).isEqualTo(first.retrievedAt());
        assertThat(second.limitations()).anyMatch(note->note.startsWith("CACHED_SNAPSHOT"));verify(p,times(1)).fetch(any());
        clock.now=clock.now.plusSeconds(31);when(p.fetch(any())).thenThrow(new ProviderException(true));
        assertThatThrownBy(()->access.fetch(p,query(clock.now))).isInstanceOf(ProviderException.class);
        assertThat(access.fetch(p,query(clock.now)).coverageStatus()).isEqualTo(CoverageStatus.UNAVAILABLE);
    }
    @Test void backoffPreventsImmediateRetriesAndRecoversOnLaterRequest() {
        var clock=new MutableClock();var access=new HazardProviderAccess(clock);var p=provider("USGS");
        when(p.fetch(any())).thenThrow(new ProviderException(true)).thenReturn(new ProviderResult(List.of(),false));
        assertThatThrownBy(()->access.fetch(p,query(clock.now))).isInstanceOf(ProviderException.class);
        assertThat(access.fetch(p,query(clock.now)).limitations()).anyMatch(note->note.startsWith("PROVIDER_BACKOFF"));
        verify(p,times(1)).fetch(any());clock.now=clock.now.plusSeconds(6);
        assertThat(access.fetch(p,query(clock.now)).coverageStatus()).isEqualTo(CoverageStatus.COMPLETE);verify(p,times(2)).fetch(any());
    }
    @Test void failedProviderDoesNotConsumeAnotherProvidersConcurrencyBudget() throws Exception {
        var clock=new MutableClock();var access=new HazardProviderAccess(clock);var firms=provider("FIRMS");var usgs=provider("USGS");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(firms.fetch(any())).thenAnswer(call->{entered.countDown();release.await(5,TimeUnit.SECONDS);return new ProviderResult(List.of(),false);});
        when(usgs.fetch(any())).thenReturn(new ProviderResult(List.of(),false));
        try(var executor=Executors.newSingleThreadExecutor()) {
            var running=executor.submit(()->access.fetch(firms,query(clock.now)));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(access.fetch(firms,query(clock.now)).limitations()).anyMatch(note->note.startsWith("PROVIDER_BUSY"));
            assertThat(access.fetch(usgs,query(clock.now)).coverageStatus()).isEqualTo(CoverageStatus.COMPLETE);
            release.countDown();running.get(5,TimeUnit.SECONDS);
        } finally {release.countDown();}
    }
    @Test void sourceHealthKeepsConfigurationCompletenessAndFreshnessDistinct() {
        var missing=new ProviderStatus("FIRMS",CoverageStatus.UNAVAILABLE,null,List.of("NOT_CONFIGURED: key absent"));
        assertThat(missing.availability()).isEqualTo("NOT_CONFIGURED");assertThat(missing.freshness()).isEqualTo("UNKNOWN");
        var stale=new ProviderStatus("GDACS",CoverageStatus.PARTIAL,Instant.now(),List.of("STALE_FEED: old publication"));
        assertThat(stale.availability()).isEqualTo("DEGRADED");assertThat(stale.freshness()).isEqualTo("STALE");
    }
}
