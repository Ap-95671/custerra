package com.nci.backend.weather.provider;
import com.nci.backend.weather.controller.WeatherController;
import com.nci.backend.weather.service.WeatherService;
import com.nci.backend.weather.model.WeatherQuery;
import com.nci.backend.provider.ProviderException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(WeatherController.class)
@Import(WeatherService.class)
class WeatherControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean WeatherProvider provider;
    @Test void returnsNormalizedContextWithOneProviderCall() throws Exception {
        var query = new WeatherQuery(32.24,77.19);
        var result = new OpenMeteoMapper().map(JsonMapper.builder().build().readValue(OpenMeteoTests.PAYLOAD,OpenMeteoPayload.class),query,Instant.parse("2026-01-01T00:10:00Z"));
        when(provider.fetch(query)).thenReturn(result);
        mvc.perform(get("/api/v1/weather/location?latitude=32.24&longitude=77.19"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.temperatureC").value(-2.5))
                .andExpect(jsonPath("$.informationType").value("MODELED_WEATHER"))
                .andExpect(jsonPath("$.intervalSeconds").value(900));
        verify(provider,times(1)).fetch(query);
    }
    @Test void invalidRequestsNeverCallProvider() throws Exception {
        for(String query:new String[]{"","latitude=0","latitude=NaN&longitude=0","latitude=91&longitude=0","latitude=0&longitude=Infinity"})
            mvc.perform(get("/api/v1/weather/location?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(provider);
    }
    @Test void failuresUseExistingErrorContract() throws Exception {
        when(provider.fetch(any())).thenThrow(new ProviderException(false),new ProviderException(true));
        for(int code:new int[]{502,504}) mvc.perform(get("/api/v1/weather/location?latitude=0&longitude=0"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.status").value(code));
    }
}
