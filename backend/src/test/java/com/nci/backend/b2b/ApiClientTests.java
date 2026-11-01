package com.nci.backend.b2b;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nci.backend.auth.AccountRepository;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.route.service.RouteRiskService;
import com.nci.backend.risk.service.RiskService;
import com.nci.backend.provider.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="CUSTERRA_DATABASE_TESTS",matches="true")
class ApiClientTests {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ApiClientService clients;@Autowired AccountRepository accounts;
 @MockitoBean NearbyHazardService nearby;@MockitoBean RouteRiskService routes;@MockitoBean RiskService risk;
 UUID owner,stranger;String name,other;ApiClientService.Issued issued;
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));}
 @BeforeEach void setup(){name="api_"+UUID.randomUUID().toString().replace("-","");other="api_"+UUID.randomUUID().toString().replace("-","");owner=accounts.create(name,"unusable");stranger=accounts.create(other,"unusable");issued=clients.create(owner,"Fixture client");}
 @AfterEach void clean(){for(UUID id:List.of(owner,stranger)){jdbc.update("delete from api_organization where owner_id=?",id);jdbc.update("delete from user_account where id=?",id);}}
 @Test void highEntropyKeyIsOnlyReturnedOnceAndOwnershipEnforced() throws Exception {
  assertThat(issued.apiKey()).matches("cst_[A-Za-z0-9_-]{43}");assertThat(issued.toString()).doesNotContain(issued.apiKey());
  assertThat(jdbc.queryForObject("select key_hash from api_client where id=?",String.class,issued.id())).matches("[a-f0-9]{64}").doesNotContain(issued.apiKey());
  mvc.perform(get("/api/v1/developer/clients").with(user(name))).andExpect(status().isOk()).andExpect(jsonPath("$[0].apiKey").doesNotExist()).andExpect(jsonPath("$[0].keyHash").doesNotExist());
  mvc.perform(get("/api/v1/developer/clients").with(user(other))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(delete("/api/v1/developer/clients/"+issued.id()).with(user(other)).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/developer/clients/"+issued.id()+"/rotate").with(user(other)).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/developer/clients").with(user(name)).contentType("application/json").content("{\"name\":\"New\"}")).andExpect(status().isForbidden());
 }
 @Test void apiKeysAndConsumerSessionsCannotAuthenticateEachOthersEndpoints() throws Exception {
  mvc.perform(get("/api/v1/b2b/hazards/nearby?latitude=0&longitude=0").with(user(name))).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/journeys").header("Authorization","Bearer "+issued.apiKey())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/developer/clients").header("Authorization","Bearer "+issued.apiKey())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/internal/operations/providers").header("Authorization","Bearer "+issued.apiKey())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/b2b/hazards/nearby?latitude=NaN&longitude=0").header("Authorization","Bearer "+issued.apiKey())).andExpect(status().isBadRequest());
  verifyNoInteractions(nearby);
 }
 @Test void scopedEvidenceAndRouteResponsesRecordSuccessfulAndFailedUsage() throws Exception {
  var now=Instant.now();var query=new HazardQuery(0,0,100,now.minusSeconds(86400),now);
  var result=new com.nci.backend.hazard.dto.NearbyHazardResponse(query,now,List.of(),false,List.of(),"Fixture",CoverageStatus.COMPLETE,List.of());
  when(nearby.earthquakes(0,0,100,7)).thenReturn(result);
  mvc.perform(get("/api/v1/b2b/hazards/nearby?latitude=0&longitude=0").header("Authorization","Bearer "+issued.apiKey()))
    .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("USGS_EARTHQUAKE_OBSERVATIONS_ONLY")).andExpect(jsonPath("$.confidence").value("UNKNOWN")).andExpect(header().exists("X-Request-ID"));
  when(routes.assess(any())).thenReturn(com.nci.backend.journey.FeatureFixtures.assessment(false));
  mvc.perform(post("/api/v1/b2b/risk/route").header("Authorization","Bearer "+issued.apiKey()).contentType("application/json").content("{\"points\":[{\"latitude\":0,\"longitude\":0},{\"latitude\":0,\"longitude\":1}],\"corridorKm\":25,\"days\":7,\"minMagnitude\":4}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.data.overallRisk").value("UNKNOWN"));
  mvc.perform(get("/api/v1/b2b/hazards/nearby?latitude=1000&longitude=0").header("Authorization","Bearer "+issued.apiKey())).andExpect(status().isBadRequest());
  assertThat(clients.usage(owner).requestsToday()).isEqualTo(3);
  assertThat(jdbc.queryForObject("select count(*) from api_usage_record where client_id=? and status=400",Integer.class,issued.id())).isEqualTo(1);
 }
 @Test void rotationRevocationExpiryAndClientLimitAreEnforced(){
  var rotated=clients.rotate(owner,issued.id());assertThat(rotated.apiKey()).isNotEqualTo(issued.apiKey());
  assertThatThrownBy(()->clients.admit(issued.apiKey(),"nearby")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  clients.revoke(owner,rotated.id());assertThatThrownBy(()->clients.admit(rotated.apiKey(),"nearby")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  var expired=clients.create(owner,"Expired");jdbc.update("update api_client set expires_at=now()-interval '1 second' where id=?",expired.id());
  assertThatThrownBy(()->clients.admit(expired.apiKey(),"nearby")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  for(int i=0;i<5;i++)clients.create(owner,"Active");assertThatThrownBy(()->clients.create(owner,"Too many")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void minuteLimitDoesNotConsumeDailyQuotaAndOldWindowsDoNotBlock() throws Exception {
  UUID org=jdbc.queryForObject("select id from api_organization where owner_id=?",UUID.class,owner);
  var minute=Instant.now().truncatedTo(ChronoUnit.MINUTES);
  jdbc.update("insert into api_usage_bucket values (?,?,'MINUTE',20)",org,java.sql.Timestamp.from(minute));
  assertThatThrownBy(()->clients.admit(issued.apiKey(),"nearby")).isInstanceOf(ApiClientService.QuotaExceeded.class);
  assertThat(clients.usage(owner).requestsToday()).isZero();
  jdbc.update("update api_usage_bucket set window_start=? where organization_id=?",java.sql.Timestamp.from(minute.minusSeconds(60)),org);
  clients.admit(issued.apiKey(),"nearby");assertThat(clients.usage(owner).requestsToday()).isEqualTo(1);
  mvc.perform(put("/api/internal/organizations/"+org+"/plan").with(user(name)).with(csrf()).contentType("application/json").content("{\"plan\":\"STANDARD\"}")).andExpect(status().isForbidden());
  assertThat(clients.usage(owner).plan()).isEqualTo("DEVELOPMENT");
 }
 @Test void operationScopesAreCheckedBeforeQuotaAndSurviveRotation() throws Exception {
  var restricted=clients.create(owner,"Nearby only",Set.of("nearby"));
  assertThatThrownBy(()->clients.create(owner,"Invalid",Set.of("admin"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  mvc.perform(get("/api/v1/b2b/risk/location?latitude=0&longitude=0").header("Authorization","Bearer "+restricted.apiKey())).andExpect(status().isForbidden());
  assertThat(clients.usage(owner).requestsToday()).isZero();
  var rotated=clients.rotate(owner,restricted.id());
  assertThat(clients.list(owner)).filteredOn(c->c.id().equals(rotated.id())).singleElement().satisfies(c->assertThat(c.scopes()).containsExactly("nearby"));
  assertThatThrownBy(()->clients.admit(rotated.apiKey(),"route")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  clients.admit(rotated.apiKey(),"nearby");assertThat(clients.usage(owner).requestsToday()).isEqualTo(1);
 }
 @Test void concurrentKeysShareAtomicOrganizationQuota() throws Exception {
  var second=clients.create(owner,"Second key");UUID org=jdbc.queryForObject("select id from api_organization where owner_id=?",UUID.class,owner);
  jdbc.update("insert into api_usage_bucket values (?,?,'DAY',99)",org,java.sql.Timestamp.from(Instant.now().truncatedTo(ChronoUnit.DAYS)));
  try(var pool=Executors.newFixedThreadPool(2)){
   var tasks=List.of(issued.apiKey(),second.apiKey()).stream().map(key->pool.submit(()->{try{clients.admit(key,"nearby");return true;}catch(ApiClientService.QuotaExceeded exhausted){return false;}})).toList();
   int successes=0;for(var task:tasks)if(task.get(5,TimeUnit.SECONDS))successes++;assertThat(successes).isEqualTo(1);
  }
  assertThat(clients.usage(owner).requestsToday()).isEqualTo(100);
  mvc.perform(get("/api/v1/b2b/hazards/nearby?latitude=0&longitude=0").header("Authorization","Bearer "+issued.apiKey())).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
  verifyNoInteractions(nearby);
 }
}
