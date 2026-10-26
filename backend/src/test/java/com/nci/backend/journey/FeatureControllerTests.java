package com.nci.backend.journey;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.nci.backend.history.*;
import com.nci.backend.route.controller.RouteController;
import com.nci.backend.route.service.RouteRiskService;
import com.nci.backend.monitoring.controller.MonitoringController;
import com.nci.backend.monitoring.service.TripMonitoringService;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest({HistoryController.class,RouteController.class,MonitoringController.class})
class FeatureControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean HistoryService history;
    @MockitoBean RouteRiskService routes;
    @MockitoBean TripMonitoringService journeys;
    @Test void rejectsMissingBodiesAndInvalidNestedCoordinates() throws Exception {
        mvc.perform(post("/api/v1/history/earthquakes").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/journeys").contentType("application/json").content("{\"name\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/routes/assessment").contentType("application/json").content("""
            {"points":[{"latitude":91,"longitude":0},{"latitude":0,"longitude":1}],"corridorKm":25,"days":7,"minMagnitude":4}
            """)).andExpect(status().isBadRequest());
        verifyNoInteractions(history,routes,journeys);
    }
    @Test void validRouteAndProviderFailureKeepJsonContract() throws Exception {
        when(routes.assess(any())).thenReturn(FeatureFixtures.assessment(false));
        String body = """
                {"points":[{"latitude":0,"longitude":0},{"latitude":0,"longitude":2}],"corridorKm":25,"days":7,"minMagnitude":4}
                """;
        mvc.perform(post("/api/v1/routes/assessment").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overallRisk").value("UNKNOWN"));
        when(routes.assess(any())).thenThrow(new com.nci.backend.provider.ProviderException(false));
        mvc.perform(post("/api/v1/routes/assessment").contentType("application/json").content(body))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502));
    }

    @Test void nullOrMissingHistoricalCoordinatesCannotSilentlyBecomeZero() throws Exception {
        for (String coordinates : new String[]{"", "\"latitude\":null,\"longitude\":0,"}) {
            String body = "{" + coordinates + "\"radiusKm\":100,\"from\":\"2025-01-01T00:00:00Z\",\"to\":\"2026-01-01T00:00:00Z\",\"minMagnitude\":4}";
            mvc.perform(post("/api/v1/history/earthquakes").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(history);
    }
}
