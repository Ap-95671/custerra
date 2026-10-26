package com.nci.backend.hazard;

import com.nci.backend.hazard.model.*;
import com.nci.backend.hazard.service.*;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class NearbyHazardTests {
    private final GeoDistance distance = new GeoDistance();
    private final Instant now = Instant.parse("2026-01-08T00:00:00Z");

    @Test
    void distanceHandlesDateLinePolesAndAntipodes() {
        assertThat(distance.kilometers(0, 0, 0, 0)).isZero();
        assertThat(distance.kilometers(0, 179.9, 0, -179.9)).isCloseTo(22.239, within(0.001));
        assertThat(distance.kilometers(90, -180, 90, 180)).isCloseTo(0, within(0.00001));
        assertThat(distance.kilometers(0, 0, 0, 180)).isCloseTo(20015.114, within(0.001));
    }

    @Test
    void filtersByActualDistanceAndWindowAndKeepsTruncation() {
        HazardProvider provider = mock(HazardProvider.class);
        when(provider.source()).thenReturn("test");
        when(provider.metadata()).thenReturn(new ProviderMetadata("test", "test", "test", "test", "test"));
        when(provider.fetch(any())).thenReturn(new ProviderResult(List.of(
                event("near", -179.9, now.minusSeconds(1)), event("far", 0, now),
                event("old", 179.9, now.minus(Duration.ofDays(8))), event("future", 179.9, now.plusSeconds(1)),
                event("center", 179.9, now)), true));
        var service = new NearbyHazardService(List.of(provider), distance, Clock.fixed(now, ZoneOffset.UTC));
        var result = service.nearby(0, 179.9, 100, 7);
        assertThat(result.items()).extracting(i -> i.event().externalId()).containsExactly("center", "near");
        assertThat(result.truncated()).isTrue();
        assertThat(result.query().from()).isEqualTo(now.minus(Duration.ofDays(7)));
        verify(provider, times(1)).fetch(any());
    }

    @Test
    void providerFailureIsNeverConvertedToEmptyData() {
        HazardProvider provider = mock(HazardProvider.class);
        when(provider.source()).thenReturn("test");
        when(provider.metadata()).thenReturn(new ProviderMetadata("test", "test", "test", "test", "test"));
        when(provider.fetch(any())).thenThrow(new ProviderException(false));
        var service = new NearbyHazardService(List.of(provider), distance, Clock.fixed(now, ZoneOffset.UTC));
        var result = service.nearby(0, 0, 100, 7);
        assertThat(result.coverageStatus()).isEqualTo(CoverageStatus.UNAVAILABLE);
        assertThat(result.providerStatuses().getFirst().limitations()).contains("PROVIDER_UNAVAILABLE");
    }

    @Test
    void queryRejectsNonFiniteCoordinatesAndExcessiveRange() {
        assertThatThrownBy(() -> new HazardQuery(Double.NaN, 0, 100, now.minusSeconds(1), now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HazardQuery(0, 0, 1001, now.minusSeconds(1), now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HazardQuery(0, 0, 100, now.minus(Duration.ofDays(31)), now)).isInstanceOf(IllegalArgumentException.class);
    }

    private NormalizedHazardEvent event(String id, double longitude, Instant started) {
        return new NormalizedHazardEvent(id, HazardType.EARTHQUAKE, "test", SeverityLevel.UNKNOWN,
                InformationType.OBSERVED_EVENT, 0, longitude, null, started, null, "test",
                "https://example.test", now, now, ConfidenceLevel.UNKNOWN, Map.of());
    }
}
