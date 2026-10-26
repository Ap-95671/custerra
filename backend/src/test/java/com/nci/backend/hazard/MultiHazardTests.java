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

class MultiHazardTests {
    final Instant now = Instant.parse("2026-10-09T12:00:00Z");
    HazardProvider provider(String source, HazardType type, ProviderResult result) {
        var provider = mock(HazardProvider.class);
        when(provider.source()).thenReturn(source);
        when(provider.supportedHazards()).thenReturn(Set.of(type));
        when(provider.metadata()).thenReturn(new ProviderMetadata(source,source,"https://example.test","https://example.test","test"));
        when(provider.fetch(any())).thenReturn(result); return provider;
    }
    NearbyHazardService service(HazardProvider... providers) {
        return new NearbyHazardService(List.of(providers),new GeoDistance(),Clock.fixed(now,ZoneOffset.UTC));
    }
    NormalizedHazardEvent event(String source, String id, Instant updated) {
        return new NormalizedHazardEvent(id,HazardType.EARTHQUAKE,"test",SeverityLevel.UNKNOWN,
                InformationType.OBSERVED_EVENT,0,0,null,now,null,source,"https://example.test",now,updated,ConfidenceLevel.UNKNOWN,Map.of());
    }
    @Test void failureIsIsolatedAndCoverageCannotBecomeComplete() {
        var usgs=provider("USGS",HazardType.EARTHQUAKE,new ProviderResult(List.of(event("USGS","1",now)),false));
        var firms=provider("FIRMS",HazardType.WILDFIRE,new ProviderResult(List.of(),false));
        when(firms.fetch(any())).thenThrow(new ProviderException(true));
        var result=service(usgs,firms).nearby(0,0,100,7);
        assertThat(result.items()).hasSize(1); assertThat(result.coverageStatus()).isEqualTo(CoverageStatus.PARTIAL);
        assertThat(result.providerStatuses().get(1).limitations()).contains("PROVIDER_TIMEOUT");
        assertThat(result.providerStatuses().get(1).retrievedAt()).isNull();
    }
    @Test void deduplicationKeepsLatestSourceRevisionAndNeverMergesProviders() {
        var usgs=provider("USGS",HazardType.EARTHQUAKE,new ProviderResult(List.of(event("USGS","1",now),event("USGS","1",now.minusSeconds(1))),false));
        var gdacs=provider("GDACS",HazardType.EARTHQUAKE,new ProviderResult(List.of(event("GDACS","1",now)),false));
        var result=service(usgs,gdacs).nearby(0,0,100,7);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).allSatisfy(i->assertThat(i.event().sourceUpdatedAt()).isEqualTo(now));
    }
    @Test void filtersAvoidCallingUnrelatedSourcesAndUnsupportedHazardsStayUnknown() {
        var usgs=provider("USGS",HazardType.EARTHQUAKE,new ProviderResult(List.of(event("USGS","1",now)),false));
        var firms=provider("FIRMS",HazardType.WILDFIRE,new ProviderResult(List.of(),false));
        var service=service(usgs,firms);
        assertThat(service.nearby(0,0,100,7,HazardType.WILDFIRE,null).sources()).extracting(ProviderMetadata::id).containsExactly("FIRMS");
        verify(usgs,never()).fetch(any());
        assertThat(service.nearby(0,0,100,7,HazardType.TORNADO,null).coverageStatus()).isEqualTo(CoverageStatus.UNKNOWN);
        assertThatThrownBy(()->service.nearby(0,0,100,7,null,"bad")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void earthquakeConsumerDoesNotQueryNewProvidersOrSuppressUsgsFailure() {
        var usgs=provider("USGS",HazardType.EARTHQUAKE,new ProviderResult(List.of(),false));
        var gdacs=provider("GDACS",HazardType.EARTHQUAKE,new ProviderResult(List.of(),false));
        var firms=provider("FIRMS",HazardType.WILDFIRE,new ProviderResult(List.of(),false));
        var service=service(usgs,gdacs,firms);
        assertThat(service.earthquakes(0,0,100,7).coverageStatus()).isEqualTo(CoverageStatus.COMPLETE);
        verify(gdacs,never()).fetch(any()); verify(firms,never()).fetch(any());
        when(usgs.fetch(any())).thenThrow(new ProviderException(true));
        assertThatThrownBy(()->service.earthquakes(0,0,100,7)).isInstanceOfSatisfying(ProviderException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(504));
    }
    @Test void ongoingEventOverlapsWindowEvenWhenItStartedEarlier() {
        var ongoing=new NormalizedHazardEvent("FL:1",HazardType.FLOOD,"test",SeverityLevel.HIGH,InformationType.ADVISORY,
                0,0,null,now.minus(Duration.ofDays(20)),now,"GDACS","https://example.test",now,now,ConfidenceLevel.UNKNOWN,Map.of());
        var gdacs=provider("GDACS",HazardType.FLOOD,new ProviderResult(List.of(ongoing),false,CoverageStatus.PARTIAL,List.of("recent feed")));
        assertThat(service(gdacs).nearby(0,0,100,7).items()).hasSize(1);
    }
    @Test void legacyStoredJsonWithoutAddedFieldsStillDeserializes() {
        var mapper=new tools.jackson.databind.json.JsonMapper();
        String old=mapper.writeValueAsString(event("USGS","1",now));
        var tree=mapper.readTree(old); ((tools.jackson.databind.node.ObjectNode)tree).remove("attributes");
        ((tools.jackson.databind.node.ObjectNode)tree).remove("retrievedAt");
        var restored=mapper.treeToValue(tree,NormalizedHazardEvent.class);
        assertThat(restored.attributes()).isEmpty(); assertThat(restored.retrievedAt()).isEqualTo(now);
    }
}
