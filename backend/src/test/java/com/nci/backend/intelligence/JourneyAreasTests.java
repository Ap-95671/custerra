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
class JourneyAreasTests {
 @Autowired MockMvc mvc;@Autowired AccountRepository accounts;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
 @MockitoBean IntelligenceService intelligence;
 @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));}
 @Test void ownedRemainingSegmentsPersistAcrossFailureAndCascadeOnDeletion()throws Exception{
  String owner="areas_"+UUID.randomUUID().toString().replace("-","").substring(0,12);accounts.create(owner,"unused-fixture-hash");accounts.create(owner+"_other","unused-fixture-hash");
  String body="{\"name\":\"Synthetic source-area journey\",\"route\":{\"points\":[{\"latitude\":0,\"longitude\":0},{\"latitude\":0,\"longitude\":1},{\"latitude\":0,\"longitude\":2}],\"corridorKm\":25,\"days\":7,\"minMagnitude\":4}}";
  var created=mvc.perform(post("/api/v1/journeys").with(user(owner)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn();String id=json.readTree(created.getResponse().getContentAsString()).get("id").asText(),path="/api/v1/journeys/"+id;
  mvc.perform(patch(path).with(user(owner)).with(csrf()).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
  mvc.perform(put(path+"/progress").with(user(owner)).with(csrf()).contentType("application/json").content("{\"position\":{\"latitude\":0,\"longitude\":1.2},\"observedAt\":\""+Instant.now()+"\",\"accuracyMeters\":5}")).andExpect(status().isOk()).andExpect(jsonPath("$.progress.segmentIndex").value(1));
  var parser=new IntelligenceTests();var event=parser.parser().parseNws(json.readTree(parser.nws("","null")),IntelligenceTests.NOW).items().getFirst();
  var fixture=new IntelligenceService.RouteExposure(IntelligenceTests.NOW,"UNKNOWN","PARTIAL",List.of(new IntelligenceService.RouteItem(event,"CURRENT",new SpatialGeometry.RouteMatch(true,1.,List.of(0),"SYNTHETIC"))),List.of(),false,List.of("Synthetic test only"));when(intelligence.route(anyList())).thenReturn(fixture);
  mvc.perform(post(path+"/areas").with(user(owner))).andExpect(status().isForbidden());
  mvc.perform(get(path+"/areas").with(user(owner+"_other"))).andExpect(status().isNotFound());mvc.perform(post(path+"/areas").with(user(owner+"_other")).with(csrf())).andExpect(status().isNotFound());verifyNoInteractions(intelligence);
  mvc.perform(post(path+"/areas").with(user(owner)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.fromSegment").value(1)).andExpect(jsonPath("$.assessment.items[0].spatial.segmentIndices[0]").value(1));
  verify(intelligence).route(argThat(points->points.size()==2&&points.getFirst().longitude()==1));
  when(intelligence.route(anyList())).thenReturn(new IntelligenceService.RouteExposure(Instant.now(),"UNKNOWN","UNAVAILABLE",List.of(),List.of(),false,List.of()));
  mvc.perform(post(path+"/areas").with(user(owner)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.error").value("SOURCES_UNAVAILABLE_PREVIOUS_ASSESSMENT_RETAINED")).andExpect(jsonPath("$.assessment.assessedAt").value(IntelligenceTests.NOW.toString()));
  mvc.perform(get(path+"/areas").with(user(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.assessment.items.length()").value(1));
  mvc.perform(delete(path).with(user(owner)).with(csrf())).andExpect(status().isNoContent());assertThat(jdbc.queryForObject("select count(*) from journey_area_evidence where journey_id=?",Integer.class,UUID.fromString(id))).isZero();
 }
 @Test void malformedComparisonNeverTreatsMissingCoordinatesAsZero()throws Exception{
  mvc.perform(post("/api/v1/intelligence/compare").with(csrf()).contentType("application/json").content("{\"destinations\":[{\"name\":\"A\"},{\"name\":\"B\"}],\"radiusKm\":100,\"days\":7}")).andExpect(status().isBadRequest());verifyNoInteractions(intelligence);
 }
}
