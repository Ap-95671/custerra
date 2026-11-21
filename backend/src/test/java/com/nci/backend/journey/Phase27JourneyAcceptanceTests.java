package com.nci.backend.journey;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import com.nci.backend.route.provider.*;
import com.nci.backend.route.dto.RoutePoint;
import com.nci.backend.provider.model.*;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.*;
import com.nci.backend.alert.provider.PushGateway;
import com.nci.backend.alert.service.NotificationDispatcher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full local controller/security/DB workflow; all upstream and push responses are synthetic. */
@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="CUSTERRA_DATABASE_TESTS",matches="true")
class Phase27JourneyAcceptanceTests {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @Autowired NotificationDispatcher dispatcher;
    @MockitoBean GeocodingProvider geocoder;@MockitoBean RouteProvider router;
    @MockitoBean NearbyHazardService nearby;@MockitoBean PushGateway push;
    String username="phase27_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        String url=System.getenv("TEST_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/custerra_[a-zA-Z0-9_]+_test"))throw new IllegalArgumentException("Use isolated loopback synthetic database");
        p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));
    }
    @AfterEach void cleanup(){
        jdbc.update("delete from monitored_journey where owner_id=(select id from user_account where username=?)",username);
        jdbc.update("delete from user_account where username=?",username);
    }
    @Test void originToDenseJourneyProgressEvidenceDedupFailureAndCancelledDelivery() throws Exception {
        var now=Instant.now();var source=new ProviderMetadata("SYNTHETIC","Controlled fixtures","https://example.test","https://example.test","Test only");
        var points=java.util.stream.IntStream.range(0,2001).mapToObj(i->new RoutePoint(0.,i/1000.)).toList();
        // Polyline6: each following vertex advances longitude by 0.001 degrees.
        String encoded="??"+"?o}@".repeat(2000);
        assertThat(EncodedRoute.decode(encoded)).isEqualTo(points);
        when(router.route(any(),any(),any())).thenReturn(new RouteProvider.RoutePlan(encoded,6,points,222.4,10000,source,now));
        when(geocoder.search(anyString())).thenReturn(new GeocodingProvider.Places(List.of(new GeocodingProvider.Place("fixture","Synthetic origin","Test region","Test country",points.getFirst())),source,now));
        var events=new AtomicReference<List<NormalizedHazardEvent>>(List.of());
        org.mockito.stubbing.Answer<NearbyHazardResponse> snapshot=call->{var at=Instant.now();return new NearbyHazardResponse(new HazardQuery(0,0,1000,at.minusSeconds(604800),at),at,
                events.get().stream().map(e->new NearbyHazardResponse.Item(e,50)).toList(),false,List.of(source),"SYNTHETIC_ONLY",CoverageStatus.COMPLETE,
                List.of(new ProviderStatus("USGS",CoverageStatus.COMPLETE,at,List.of("SYNTHETIC_ONLY"))));};
        when(nearby.earthquakes(anyDouble(),anyDouble(),anyDouble(),anyInt())).thenAnswer(snapshot);
        doAnswer(snapshot).when(nearby).nearby(anyDouble(),anyDouble(),anyDouble(),anyInt());
        when(push.configured()).thenReturn(true);when(push.send(any(),anyString(),anyString())).thenReturn(PushGateway.Result.sent());
        var csrf=mvc.perform(get("/api/v1/auth/csrf")).andReturn();var session=(MockHttpSession)csrf.getRequest().getSession();
        mvc.perform(post("/api/v1/auth/register").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",username,"password","Synthetic-workflow-password-2026")))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/login").session(session).with(csrf()).param("username",username).param("password","Synthetic-workflow-password-2026")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/geocoding/search?q=Synthetic").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.candidates[0].name").value("Synthetic origin"));
        String plan="{\"origin\":{\"latitude\":0,\"longitude\":0},\"destination\":{\"latitude\":0,\"longitude\":2},\"mode\":\"DRIVING\",\"corridorKm\":25,\"days\":7,\"minMagnitude\":4}";
        mvc.perform(post("/api/v1/routes/plan").session(session).with(csrf()).contentType("application/json").content(plan)).andExpect(status().isOk()).andExpect(jsonPath("$.segmentCount").value(2000));
        var saved=mvc.perform(post("/api/v1/journeys/generated").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("name","SYNTHETIC acceptance journey","originName","Fixture A","destinationName","Fixture B","geometry",encoded,"corridorKm",25,"days",7,"minMagnitude",4)))).andExpect(status().isCreated()).andExpect(jsonPath("$.route.points.length()").value(2001)).andReturn();
        String journey=json.readTree(saved.getResponse().getContentAsString()).get("id").asText(),path="/api/v1/journeys/"+journey;
        mvc.perform(patch(path).session(session).with(csrf()).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
        var keys=com.nci.backend.alert.PushFixtures.keys();
        mvc.perform(patch("/api/v1/notifications/preferences").session(session).with(csrf()).contentType("application/json").content("{\"enabled\":true,\"pushEnabled\":true,\"hazardTypes\":[\"EARTHQUAKE\"],\"minimumSeverity\":\"UNKNOWN\",\"includeUnknown\":true}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/notifications/subscriptions").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("endpoint","https://fcm.googleapis.com/fcm/send/phase27-"+UUID.randomUUID(),"p256dh",keys.publicKey(),"auth",keys.auth())))).andExpect(status().isCreated());
        mvc.perform(post(path+"/check").session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(put(path+"/progress").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("position",Map.of("latitude",0,"longitude",.5),"observedAt",Instant.now(),"accuracyMeters",5)))).andExpect(status().isOk()).andExpect(jsonPath("$.progress.state").value("MATCHED"));
        var event=new NormalizedHazardEvent("phase27-synthetic",HazardType.EARTHQUAKE,"SYNTHETIC TEST — no real disaster",SeverityLevel.UNKNOWN,InformationType.OBSERVED_EVENT,0,1,null,now.minusSeconds(30),null,"USGS","https://example.test/synthetic",now,now,ConfidenceLevel.UNKNOWN,Map.of("magnitude",4.2));events.set(List.of(event));
        mvc.perform(post(path+"/check").session(session).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.assessment.matches.length()").value(1));
        mvc.perform(post(path+"/check").session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(path+"/notices").session(session)).andExpect(jsonPath("$.length()").value(1));
        dispatcher.dispatch();verify(push,times(1)).send(any(),anyString(),anyString());
        when(nearby.nearby(anyDouble(),anyDouble(),anyDouble(),anyInt())).thenThrow(new com.nci.backend.provider.ProviderException(true));
        mvc.perform(post(path+"/check").session(session).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.lastError").value("PROVIDER_TIMEOUT")).andExpect(jsonPath("$.providerCheckpoints.length()").value(1));
        doAnswer(snapshot).when(nearby).nearby(anyDouble(),anyDouble(),anyDouble(),anyInt());
        events.set(List.of(new NormalizedHazardEvent(event.externalId(),event.hazardType(),event.title(),event.severity(),event.informationType(),0,1,null,event.startedAt(),null,"USGS",event.sourceUrl(),Instant.now(),Instant.now(),ConfidenceLevel.UNKNOWN,Map.of("magnitude",5.1))));
        mvc.perform(post(path+"/check").session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(path+"/notices").session(session)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(patch(path).session(session).with(csrf()).contentType("application/json").content("{\"status\":\"PAUSED\"}")).andExpect(status().isOk());
        dispatcher.dispatch();verify(push,times(1)).send(any(),anyString(),anyString());
        mvc.perform(patch(path).session(session).with(csrf()).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
        mvc.perform(post(path+"/check").session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(path+"/notices").session(session)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(patch(path).session(session).with(csrf()).contentType("application/json").content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk());
        mvc.perform(patch(path).session(session).with(csrf()).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isConflict());
        mvc.perform(post(path+"/check").session(session).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get(path+"/evidence-history").session(session)).andExpect(jsonPath("$.length()").value(2));
    }
}
