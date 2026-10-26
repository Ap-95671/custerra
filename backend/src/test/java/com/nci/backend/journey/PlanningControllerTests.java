package com.nci.backend.journey;

import com.nci.backend.route.controller.RoutePlanningController;
import com.nci.backend.route.service.RoutePlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(RoutePlanningController.class)
class PlanningControllerTests {
    @Autowired MockMvc mvc; @MockitoBean RoutePlanningService service;
    @Test void rejectsInvalidInputsAndUnsupportedModeBeforeCallingProvider() throws Exception {
        for(String body:new String[]{"{}","{\"origin\":{\"latitude\":91,\"longitude\":0},\"destination\":{\"latitude\":0,\"longitude\":0},\"mode\":\"DRIVING\",\"corridorKm\":25,\"days\":7,\"minMagnitude\":4}",
                "{\"origin\":{\"latitude\":0,\"longitude\":0},\"destination\":{\"latitude\":0,\"longitude\":1},\"mode\":\"WALKING\",\"corridorKm\":25,\"days\":7,\"minMagnitude\":4}"})
            mvc.perform(post("/api/v1/routes/plan").contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/v1/geocoding/search?q=x")).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
}
