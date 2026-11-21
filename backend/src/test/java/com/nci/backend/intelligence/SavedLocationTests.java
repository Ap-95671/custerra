package com.nci.backend.intelligence;
import java.time.*;
import java.util.*;
import com.nci.backend.auth.AccountRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="CUSTERRA_DATABASE_TESTS",matches="true")
class SavedLocationTests {
    @Autowired MockMvc mvc;@Autowired AccountRepository accounts;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @MockitoBean IntelligenceService intelligence;
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));}
    String owner,other;
    @BeforeEach void users(){owner="place_"+UUID.randomUUID().toString().replace("-","").substring(0,12);other="other_"+UUID.randomUUID().toString().replace("-","").substring(0,12);accounts.create(owner,"fixture-not-used-for-login");accounts.create(other,"fixture-not-used-for-login");}
    String create()throws Exception{return json.readTree(mvc.perform(post("/api/v1/locations").with(user(owner)).with(csrf()).contentType("application/json").content("{\"name\":\"Fixture destination\",\"latitude\":0,\"longitude\":0,\"radiusKm\":100,\"days\":7}")).andExpect(status().isCreated()).andExpect(jsonPath("$.monitoringOptIn").value(false)).andReturn().getResponse().getContentAsString()).get("id").asText();}
    @Test void ownershipCsrfAndDeletionAreEnforced()throws Exception{
        mvc.perform(get("/api/v1/locations")).andExpect(status().isUnauthorized());mvc.perform(post("/api/v1/locations").with(user(owner)).contentType("application/json").content("{}")).andExpect(status().isForbidden());String id=create();
        for(String suffix:List.of("","/history"))mvc.perform(get("/api/v1/locations/"+id+suffix).with(user(other))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/locations/"+id+"/check").with(user(other)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/locations/"+id).with(user(other)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/locations/"+id+"/monitoring").with(user(owner)).with(csrf()).contentType("application/json").content("{\"enabled\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.monitoringOptIn").value(true));
        mvc.perform(delete("/api/v1/locations/"+id).with(user(owner)).with(csrf())).andExpect(status().isNoContent());verifyNoInteractions(intelligence);
    }
    IntelligenceService.Response response(String severity){
        var fixture=new IntelligenceTests();var event=fixture.parser().parseNws(json.readTree(fixture.nws("","{\"type\":\"Point\",\"coordinates\":[0,0]}").replace("Severe",severity)),IntelligenceTests.NOW).items().getFirst();
        return new IntelligenceService.Response(IntelligenceTests.NOW,"PARTIAL",List.of(new IntelligenceService.Item(event,"CURRENT",new SpatialGeometry.LocationMatch(true,0.,"FIXTURE"))),List.of(new IntelligenceFeed.Snapshot("NWS",IntelligenceTests.NOW,"PARTIAL",List.of(),List.of("Fixture"))),List.of(),List.of("Fixture evidence"));
    }
    @Test void baselineRevisionsAndFailedSourcePreservationAreDurable()throws Exception{
        String id=create();when(intelligence.nearby(0,0,100,7)).thenReturn(response("Severe"));
        mvc.perform(post("/api/v1/locations/"+id+"/check").with(user(owner)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.changes.length()").value(0));
        when(intelligence.nearby(0,0,100,7)).thenReturn(response("Extreme"));
        mvc.perform(post("/api/v1/locations/"+id+"/check").with(user(owner)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.changes[0].type").value("UPDATED_EVIDENCE"));
        var unavailable=new IntelligenceService.Response(Instant.now(),"UNAVAILABLE",List.of(),List.of(new IntelligenceFeed.Snapshot("NWS",null,"UNAVAILABLE",List.of(),List.of("Fixture timeout"))),List.of(),List.of());when(intelligence.nearby(0,0,100,7)).thenReturn(unavailable);
        mvc.perform(post("/api/v1/locations/"+id+"/check").with(user(owner)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.changes.length()").value(0));when(intelligence.nearby(0,0,100,7)).thenReturn(response("Extreme"));
        mvc.perform(post("/api/v1/locations/"+id+"/check").with(user(owner)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.changes.length()").value(0));
        mvc.perform(get("/api/v1/locations/"+id+"/history").with(user(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(delete("/api/v1/locations/"+id).with(user(owner)).with(csrf())).andExpect(status().isNoContent());assertThat(jdbc.queryForObject("select count(*) from saved_location_check where location_id=?",Integer.class,UUID.fromString(id))).isZero();
    }
    @Test void missingCoordinatesAndTwentyLocationBoundAreRejected()throws Exception{
        mvc.perform(post("/api/v1/locations").with(user(owner)).with(csrf()).contentType("application/json").content("{\"name\":\"Missing coordinates\",\"radiusKm\":100,\"days\":7}")).andExpect(status().isBadRequest());
        for(int i=0;i<20;i++)create();mvc.perform(post("/api/v1/locations").with(user(owner)).with(csrf()).contentType("application/json").content("{\"name\":\"Overflow\",\"latitude\":0,\"longitude\":0,\"radiusKm\":100,\"days\":7}")).andExpect(status().isConflict());
    }
}
