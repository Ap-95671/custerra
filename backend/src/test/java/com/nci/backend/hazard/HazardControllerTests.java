package com.nci.backend.hazard;

import com.nci.backend.hazard.controller.HazardController;
import com.nci.backend.hazard.dto.HazardPageResponse;
import com.nci.backend.hazard.mapper.HazardMapper;
import com.nci.backend.hazard.model.*;
import com.nci.backend.hazard.service.HazardService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(HazardController.class)
class HazardControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean HazardService service;

    @Test
    void emptyDatabaseReturnsExplicitEmptyPage() throws Exception {
        when(service.findHazards(null, null, 0, 20)).thenReturn(new HazardPageResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/hazards"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void forwardsTypedFiltersAndPagination() throws Exception {
        when(service.findHazards(HazardType.FLOOD, SeverityLevel.HIGH, 1, 10))
                .thenReturn(new HazardPageResponse(List.of(), 1, 10, 0, 0));
        mvc.perform(get("/api/v1/hazards?hazardType=FLOOD&severity=HIGH&page=1&size=10"))
                .andExpect(status().isOk());
        verify(service).findHazards(HazardType.FLOOD, SeverityLevel.HIGH, 1, 10);
    }

    @Test
    void rejectsInvalidQueriesBeforeCallingService() throws Exception {
        for (String query : new String[]{"page=-1", "page=10001", "page=2147483648", "size=0",
                "size=101", "size=oops", "hazardType=ALIEN", "severity=UNRECOGNIZED"}) {
            mvc.perform(get("/api/v1/hazards?" + query))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test
    void returnsEventDtoWithProvenance() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(new HazardMapper().toResponse(HazardFixtures.event(
                "test", HazardType.EARTHQUAKE, SeverityLevel.UNKNOWN, HazardFixtures.START)));
        mvc.perform(get("/api/v1/hazards/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hazardType").value("EARTHQUAKE"))
                .andExpect(jsonPath("$.severity").value("UNKNOWN"))
                .andExpect(jsonPath("$.informationType").value("OBSERVED_EVENT"))
                .andExpect(jsonPath("$.source").value("test-source"))
                .andExpect(jsonPath("$.startedAt").value("2026-01-01T12:00:00Z"));
    }

    @Test
    void unknownIdReturnsConsistent404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/v1/hazards/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/hazards/" + id));
    }

    @Test
    void malformedIdReturns400() throws Exception {
        mvc.perform(get("/api/v1/hazards/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void publicMutationIsNotSupported() throws Exception {
        mvc.perform(post("/api/v1/hazards")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}
