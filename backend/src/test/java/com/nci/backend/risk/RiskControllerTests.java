package com.nci.backend.risk;

import com.nci.backend.risk.controller.RiskController;
import com.nci.backend.risk.service.*;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.provider.ProviderException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.nci.backend.risk.RiskFixtures.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(RiskController.class)
@Import({RiskService.class, RiskScoringService.class, EarthquakeActivityStrategy.class})
class RiskControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean NearbyHazardService nearby;

    @Test
    void returnsCompleteScopedContractWithDefaultQuery() throws Exception {
        when(nearby.earthquakes(0, 0, 100, 7)).thenReturn(snapshot(false, event("test", 5.2, 60, Duration.ofHours(48))));
        mvc.perform(get("/api/v1/risk/location?latitude=0&longitude=0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overallRisk").value("UNKNOWN"))
                .andExpect(jsonPath("$.score").isEmpty())
                .andExpect(jsonPath("$.informationType").value("MODELED_RISK"))
                .andExpect(jsonPath("$.hazards[0].score").value(34))
                .andExpect(jsonPath("$.hazards[0].scope").value("RECENT_EARTHQUAKE_ACTIVITY"))
                .andExpect(jsonPath("$.hazards[0].confidence").value("LOW"))
                .andExpect(jsonPath("$.hazards[0].factors[0].sourceUrl").value("https://example.test/test"))
                .andExpect(jsonPath("$.hazards[1].riskLevel").value("UNKNOWN"))
                .andExpect(jsonPath("$.dataSources[0].id").value("TEST"))
                .andExpect(jsonPath("$.safetyNotice").isNotEmpty());
        verify(nearby).earthquakes(0, 0, 100, 7);
    }

    @Test
    void rejectsInvalidCoordinatesAndUnboundedQueriesBeforeFetching() throws Exception {
        for (String q : new String[]{"", "latitude=0", "latitude=91&longitude=0", "latitude=0&longitude=-181",
                "latitude=NaN&longitude=0", "latitude=0&longitude=Infinity", "latitude=0&longitude=0&radiusKm=NaN",
                "latitude=0&longitude=0&radiusKm=0", "latitude=0&longitude=0&radiusKm=1001",
                "latitude=0&longitude=0&days=0", "latitude=0&longitude=0&days=31"}) {
            mvc.perform(get("/api/v1/risk/location?" + q)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(nearby);
    }

    @Test
    void acceptsBoundaryCoordinatesAndExplicitWindow() throws Exception {
        when(nearby.earthquakes(-90, 180, 1000, 30)).thenReturn(snapshot(false));
        mvc.perform(get("/api/v1/risk/location?latitude=-90&longitude=180&radiusKm=1000&days=30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hazards[0].riskLevel").value("UNKNOWN"))
                .andExpect(jsonPath("$.hazards[0].score").isEmpty());
        verify(nearby).earthquakes(-90, 180, 1000, 30);
    }

    @Test
    void truncationIsVisibleAndWithholdsEarthquakeScore() throws Exception {
        when(nearby.earthquakes(0, 0, 100, 7)).thenReturn(snapshot(true, event("test", 7.0, 0, Duration.ZERO)));
        mvc.perform(get("/api/v1/risk/location?latitude=0&longitude=0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceResultsTruncated").value(true))
                .andExpect(jsonPath("$.hazards[0].score").isEmpty())
                .andExpect(jsonPath("$.hazards[0].factors[0].contribution").value(100));
    }

    @Test
    void providerFailuresKeepThe502And504ErrorContract() throws Exception {
        when(nearby.earthquakes(0, 0, 100, 7)).thenThrow(new ProviderException(false), new ProviderException(true));
        for (int code : new int[]{502, 504}) {
            mvc.perform(get("/api/v1/risk/location?latitude=0&longitude=0"))
                    .andExpect(status().is(code)).andExpect(jsonPath("$.status").value(code))
                    .andExpect(jsonPath("$.path").value("/api/v1/risk/location"));
        }
    }
}
