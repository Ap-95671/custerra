package com.nci.backend.operations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"OPS_TOKEN=fixture-operations-token-at-least-32-characters","custerra.providers.firms.map-key="})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="CUSTERRA_DATABASE_TESTS",matches="true")
class OperationsSecurityTests {
 @Autowired MockMvc mvc;@Autowired com.nci.backend.provider.firms.FirmsProvider firms;
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));}
 @Test void operationalDetailsRequireSeparateCredentialsAndHealthIsMinimal() throws Exception {
  mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist());
  mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/internal/operations/providers").with(user("ordinary"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/internal/operations/providers").header("Authorization","Bearer wrong")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/internal/operations/providers").header("Authorization","Bearer fixture-operations-token-at-least-32-characters")).andExpect(status().isOk());
 }
 @Test void disabledProviderIsObservedByActualAspect() throws Exception {
  var now=java.time.Instant.now();firms.fetch(new com.nci.backend.provider.model.HazardQuery(0,0,10,now.minusSeconds(86400),now));
  mvc.perform(get("/api/internal/operations/providers").header("Authorization","Bearer fixture-operations-token-at-least-32-characters"))
   .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.provider == 'FIRMS')].state").value("UNAVAILABLE"));
  mvc.perform(get("/actuator/prometheus").header("Authorization","Bearer fixture-operations-token-at-least-32-characters")).andExpect(status().isOk());
 }
 @Test void durableWorkStatusIsPrivateAndDisabledWorkersAreExplicit() throws Exception {
  mvc.perform(get("/api/internal/operations/workers")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/internal/operations/workers").header("Authorization","Bearer fixture-operations-token-at-least-32-characters")).andExpect(status().isOk());
  mvc.perform(get("/api/internal/operations/workload")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/internal/operations/workload").header("Authorization","Bearer fixture-operations-token-at-least-32-characters"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.monitoringEnabled").value(false))
   .andExpect(jsonPath("$.notificationsEnabled").value(false))
   .andExpect(jsonPath("$.journeys.due").isNumber()).andExpect(jsonPath("$.notifications.expired_leases").isNumber());
 }
}
