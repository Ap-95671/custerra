package com.nci.backend.risk;

import com.nci.backend.risk.controller.MultiHazardRiskController;
import com.nci.backend.risk.service.*;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.weather.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(MultiHazardRiskController.class)
@Import({MultiHazardRiskService.class,RiskScoringService.class,EarthquakeActivityStrategy.class,ThermalActivityStrategy.class})
class MultiHazardRiskControllerTests {
    @Autowired MockMvc mvc; @MockitoBean NearbyHazardService nearby; @MockitoBean WeatherService weather;
    @Test void exposesUnknownAndEmptyDominanceRatherThanZeroRisk() throws Exception {
        when(nearby.nearby(0,0,100,7)).thenReturn(RiskFixtures.snapshot(false));
        mvc.perform(get("/api/v1/risk/multi-hazard?latitude=0&longitude=0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.modelVersion").value("overall-risk-v2-experimental"))
                .andExpect(jsonPath("$.overallRisk").value("UNKNOWN"))
                .andExpect(jsonPath("$.score").isEmpty()).andExpect(jsonPath("$.dominantHazards").isEmpty())
                .andExpect(jsonPath("$.unassessedHazardCount").value(11));
    }
    @Test void rejectsInvalidQueryBeforeAnyProviderCalls() throws Exception {
        mvc.perform(get("/api/v1/risk/multi-hazard?latitude=NaN&longitude=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/risk/multi-hazard?latitude=0&longitude=0&days=31")).andExpect(status().isBadRequest());
        verifyNoInteractions(nearby,weather);
    }
}
