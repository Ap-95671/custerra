package com.nci.backend.hazard;

import com.nci.backend.hazard.controller.NearbyHazardController;
import com.nci.backend.hazard.controller.HazardController;
import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.service.*;
import com.nci.backend.provider.ProviderException;
import com.nci.backend.provider.model.HazardQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest({NearbyHazardController.class, HazardController.class})
class NearbyHazardControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean NearbyHazardService nearby;
    @MockitoBean HazardService stored;

    @Test
    void selectsNearbyRouteAndUsesDefaults() throws Exception {
        var to = Instant.parse("2026-01-08T00:00:00Z");
        when(nearby.nearby(32.24, 77.19, 100, 7)).thenReturn(new NearbyHazardResponse(
                new HazardQuery(32.24, 77.19, 100, to.minusSeconds(604800), to), to,
                List.of(), false, List.of(), "Earthquakes only"));
        mvc.perform(get("/api/v1/hazards/nearby?latitude=32.24&longitude=77.19"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.query.radiusKm").value(100))
                .andExpect(jsonPath("$.truncated").value(false));
        verifyNoInteractions(stored);
    }

    @Test
    void rejectsMissingInvalidAndNonFiniteParameters() throws Exception {
        for (String q : new String[]{"", "latitude=0", "latitude=91&longitude=0", "latitude=0&longitude=181",
                "latitude=NaN&longitude=0", "latitude=0&longitude=Infinity", "latitude=0&longitude=0&radiusKm=NaN",
                "latitude=0&longitude=0&radiusKm=0", "latitude=0&longitude=0&radiusKm=1001",
                "latitude=0&longitude=0&provider=UNKNOWN", "latitude=0&longitude=0&hazardType=UNKNOWN",
                "latitude=0&longitude=0&days=31", "latitude=0&longitude=0&days=0"}) {
            mvc.perform(get("/api/v1/hazards/nearby?" + q)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(nearby, stored);
    }

    @Test
    void passesProviderAndHazardFilters() throws Exception {
        mvc.perform(get("/api/v1/hazards/nearby?latitude=0&longitude=0&provider=FIRMS&hazardType=WILDFIRE"))
                .andExpect(status().isOk());
        verify(nearby).nearby(0, 0, 100, 7, com.nci.backend.hazard.model.HazardType.WILDFIRE, "FIRMS");
    }

    @Test
    void providerErrorsUseExistingApiContract() throws Exception {
        when(nearby.nearby(0, 0, 100, 7)).thenThrow(new ProviderException(false), new ProviderException(true));
        for (int status : new int[]{502, 504}) {
            mvc.perform(get("/api/v1/hazards/nearby?latitude=0&longitude=0"))
                    .andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status))
                    .andExpect(jsonPath("$.path").value("/api/v1/hazards/nearby"))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
    }
}
