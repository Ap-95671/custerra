package com.nci.backend.risk;

import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.*;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import com.nci.backend.risk.service.*;
import com.nci.backend.risk.model.RiskLevel;
import com.nci.backend.weather.service.WeatherService;
import com.nci.backend.weather.dto.WeatherResponse;
import com.nci.backend.weather.model.WeatherQuery;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiHazardRiskTests {
    final Instant now=Instant.parse("2026-10-09T12:00:00Z");
    final ProviderMetadata source=new ProviderMetadata("test","test","https://example.test","https://example.test","test");
    NearbyHazardResponse.Item thermal(String id,ConfidenceLevel confidence,double km,int ageHours) {
        var time=now.minus(Duration.ofHours(ageHours));
        return new NearbyHazardResponse.Item(new NormalizedHazardEvent(id,HazardType.WILDFIRE,"Thermal anomaly",SeverityLevel.UNKNOWN,
                InformationType.OBSERVED_EVENT,0,0,null,time,null,"FIRMS","https://example.test",time,null,confidence,Map.of("frp",20.0),now,Map.of("confidence","n")),km);
    }
    NearbyHazardResponse snapshot(CoverageStatus firms,NearbyHazardResponse.Item... items) {
        return new NearbyHazardResponse(new HazardQuery(0,0,100,now.minus(Duration.ofDays(7)),now),now,List.of(items),false,List.of(source),"test",
                firms==CoverageStatus.COMPLETE?CoverageStatus.PARTIAL:firms,List.of(new ProviderStatus("USGS",CoverageStatus.COMPLETE,now,List.of()),
                new ProviderStatus("FIRMS",firms,firms==CoverageStatus.UNAVAILABLE?null:now,List.of()),new ProviderStatus("GDACS",CoverageStatus.PARTIAL,now,List.of())));
    }
    WeatherResponse weather(double wind,boolean stale) {
        return new WeatherResponse(new WeatherQuery(0,0),0,0,"MODELED_WEATHER",now.minusSeconds(300),now,900,30.,0.,0.,wind,false,stale,source,"CC BY 4.0",List.of());
    }
    RiskScoringService scoring() { return new RiskScoringService(List.of(new EarthquakeActivityStrategy(),new ThermalActivityStrategy())); }
    @Test void thermalIndicatorIsDeterministicDoesNotSumPixelsAndRetainsEvidence() {
        var s=snapshot(CoverageStatus.COMPLETE,thermal("a",ConfidenceLevel.MEDIUM,5,1),thermal("b",ConfidenceLevel.HIGH,20,2));
        var fire=scoring().assess(s,weather(30,false)).stream().filter(h->h.hazardType()==HazardType.WILDFIRE).findFirst().orElseThrow();
        assertThat(fire.score()).isEqualTo(60);assertThat(fire.riskLevel()).isEqualTo(RiskLevel.HIGH);assertThat(fire.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(fire.evidence()).hasSize(2);assertThat(fire.environmentalContext()).hasSize(1);
        assertThat(fire.explanation()).contains("not wildfire risk");
        assertThat(scoring().assess(s,weather(30,false))).isEqualTo(scoring().assess(s,weather(30,false)));
    }
    @Test void incompleteUnknownLowConfidenceAndEmptyRemainUnknown() {
        for(var status:List.of(CoverageStatus.PARTIAL,CoverageStatus.TRUNCATED,CoverageStatus.UNAVAILABLE,CoverageStatus.UNKNOWN)) {
            var fire=scoring().assess(snapshot(status,thermal("a",ConfidenceLevel.HIGH,1,0))).stream().filter(h->h.hazardType()==HazardType.WILDFIRE).findFirst().orElseThrow();
            assertThat(fire.score()).isNull();assertThat(fire.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        }
        for(var confidence:List.of(ConfidenceLevel.LOW,ConfidenceLevel.UNKNOWN)) {
            var fire=new ThermalActivityStrategy().assess(new NearbyHazardResponse(new HazardQuery(0,0,100,now.minusSeconds(3600),now),now,List.of(thermal("a",confidence,1,0)),false,List.of(source),"test"));
            assertThat(fire.score()).isNull();
        }
        assertThat(new ThermalActivityStrategy().assess(new NearbyHazardResponse(new HazardQuery(0,0,100,now.minusSeconds(3600),now),now,List.of(),false,List.of(source),"test")).score()).isNull();
    }
    @Test void weatherNeverChangesEarthquakeScoresOrMakesThermalSpreadClaims() {
        var earthquake=new NearbyHazardResponse.Item(new NormalizedHazardEvent("eq",HazardType.EARTHQUAKE,"eq",SeverityLevel.UNKNOWN,
                InformationType.OBSERVED_EVENT,0,0,null,now,null,"USGS","https://example.test",now,now,ConfidenceLevel.UNKNOWN,Map.of("magnitude",5.)),5);
        var s=snapshot(CoverageStatus.COMPLETE,earthquake,thermal("a",ConfidenceLevel.HIGH,1,0));
        var calm=scoring().assess(s,weather(1,false)); var windy=scoring().assess(s,weather(80,false));
        assertThat(calm.getFirst()).isEqualTo(windy.getFirst());
        var fire=windy.stream().filter(h->h.hazardType()==HazardType.WILDFIRE).findFirst().orElseThrow();
        assertThat(fire.environmentalContext().getFirst().explanation()).contains("no score change or spread inference");
        assertThat(scoring().assess(s,weather(80,true)).stream().filter(h->h.hazardType()==HazardType.WILDFIRE).findFirst().orElseThrow().environmentalContext()).isEmpty();
    }
    @Test void aggregatorExposesDominanceWithoutAveragingUnknownAndWeatherFailureIsIsolated() {
        var nearby=mock(NearbyHazardService.class);var weather=mock(WeatherService.class);
        when(nearby.nearby(0,0,100,7)).thenReturn(snapshot(CoverageStatus.COMPLETE,thermal("a",ConfidenceLevel.HIGH,1,0)));
        when(weather.location(0,0)).thenThrow(new ProviderException(true));
        var result=new MultiHazardRiskService(nearby,scoring(),weather).assess(0,0,100,7);
        assertThat(result.overallRisk()).isEqualTo(RiskLevel.UNKNOWN);assertThat(result.score()).isNull();
        assertThat(result.dominantHazards()).containsExactly(HazardType.WILDFIRE);assertThat(result.assessedHazardCount()).isEqualTo(1);
        assertThat(result.unassessedHazardCount()).isEqualTo(10);assertThat(result.weatherStatus()).isEqualTo("UNAVAILABLE");
        assertThat(result.hazards().stream().filter(h->h.hazardType()==HazardType.FLOOD).findFirst().orElseThrow().riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
    }
    @Test void sourceFailureDoesNotContaminateUnrelatedStrategy() {
        var earthquake=new NearbyHazardResponse.Item(new NormalizedHazardEvent("eq",HazardType.EARTHQUAKE,"eq",SeverityLevel.UNKNOWN,
                InformationType.OBSERVED_EVENT,0,0,null,now,null,"USGS","https://example.test",now,now,ConfidenceLevel.UNKNOWN,Map.of("magnitude",5.)),5);
        assertThat(scoring().assess(snapshot(CoverageStatus.UNAVAILABLE,earthquake)).getFirst().score()).isEqualTo(60);
    }
}
