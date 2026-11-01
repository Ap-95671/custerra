package com.nci.backend.operations;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.nci.backend.auth.AccountRepository;
import com.nci.backend.b2b.ApiClientService;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.provider.model.HazardQuery;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

/** Opt-in, bounded in-process HTTP/filter/DB exercise. No upstream requests. Not a network SLO. */
@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="CUSTERRA_LOAD_TESTS",matches="true")
class LocalLoadTests {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;@Autowired ApiClientService clients;@Autowired ObjectMapper json;
    @MockitoBean NearbyHazardService nearby;
    @DynamicPropertySource static void db(DynamicPropertyRegistry p){
        String url=System.getenv("TEST_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/custerra_[a-zA-Z0-9_]+_test"))throw new IllegalArgumentException("Load test requires isolated loopback fixture database");
        p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));
    }
    @Test void boundedConcurrentRequestsThroughSecurityAndRealDatabase() throws Exception {
        String username="load_"+UUID.randomUUID().toString().replace("-","");
        UUID owner=accounts.create(username,passwords.encode("Local-fixture-password-2026"));
        var now=Instant.now();var query=new HazardQuery(0,0,100,now.minusSeconds(86400),now);
        var snapshot=new NearbyHazardResponse(query,now,List.of(new NearbyHazardResponse.Item(com.nci.backend.journey.FeatureFixtures.event("local",0,.5,4,now),1)),false,List.of(),"SIMULATED_LOCAL_ONLY");
        when(nearby.nearby(anyDouble(),anyDouble(),anyDouble(),anyInt())).thenReturn(snapshot);
        when(nearby.earthquakes(anyDouble(),anyDouble(),anyDouble(),anyInt())).thenReturn(snapshot);
        var issued=clients.create(owner,"Synthetic load fixture");var results=new LinkedHashMap<String,Object>();
        try(var pool=Executors.newFixedThreadPool(8)) {
            String route="{\"points\":[{\"latitude\":0,\"longitude\":0},{\"latitude\":0,\"longitude\":1}],\"corridorKm\":25,\"days\":7,\"minMagnitude\":4}";
            for(String operation:List.of("nearby","route","risk","authentication","saved_journeys","notifications","b2b")) {
                var jobs=new ArrayList<Future<Double>>();long began=System.nanoTime();
                for(int i=0;i<8;i++)jobs.add(pool.submit(()->{
                    MockHttpServletRequestBuilder request=switch(operation) {
                        case "nearby"->get("/api/v1/hazards/nearby?latitude=0&longitude=0");
                        case "route"->post("/api/v1/routes/assessment").with(csrf()).contentType("application/json").content(route);
                        case "risk"->get("/api/v1/risk/location?latitude=0&longitude=0");
                        case "authentication"->post("/api/v1/auth/login").with(csrf()).param("username",username).param("password","Local-fixture-password-2026");
                        case "saved_journeys"->post("/api/v1/journeys").with(user(username)).with(csrf()).contentType("application/json").content("{\"name\":\"Load fixture\",\"route\":"+route+"}");
                        case "notifications"->get("/api/v1/notifications").with(user(username));
                        default->get("/api/v1/b2b/hazards/nearby?latitude=0&longitude=0").header("Authorization","Bearer "+issued.apiKey());
                    };
                    long start=System.nanoTime();var response=mvc.perform(request).andReturn().getResponse();
                    assertThat(response.getStatus()).as(operation).isBetween(200,204);return (System.nanoTime()-start)/1e6;
                }));
                var durations=new ArrayList<Double>();for(var task:jobs)durations.add(task.get(30,TimeUnit.SECONDS));Collections.sort(durations);
                results.put(operation,Map.of("requests",8,"concurrency",8,"failures",0,"p50Ms",durations.get(3),"p95Ms",durations.get(7),"wallMs",(System.nanoTime()-began)/1e6));
            }
            results.put("scope","LOCAL_MOCKMVC_REAL_POSTGRES_SIMULATED_PROVIDER_NO_NETWORK_SLO");
            Files.writeString(Path.of("target/local-load-results.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        } finally {jdbc.update("delete from monitored_journey where owner_id=?",owner);jdbc.update("delete from api_organization where owner_id=?",owner);jdbc.update("delete from user_account where id=?",owner);}
    }
    @Test void exhaustedIsolatedPoolFailsWithinBoundAndRecovers() throws Exception {
        var config=new com.zaxxer.hikari.HikariConfig();config.setJdbcUrl(System.getenv("TEST_DB_URL"));config.setUsername(System.getenv("TEST_DB_USERNAME"));config.setPassword(System.getenv("TEST_DB_PASSWORD"));config.setMaximumPoolSize(2);config.setMinimumIdle(0);config.setConnectionTimeout(250);
        try(var pool=new com.zaxxer.hikari.HikariDataSource(config)) {
            long start;
            try(var first=pool.getConnection();var second=pool.getConnection()) {
                start=System.nanoTime();assertThatThrownBy(pool::getConnection).isInstanceOf(java.sql.SQLTransientConnectionException.class);
                assertThat(Duration.ofNanos(System.nanoTime()-start)).isBetween(Duration.ofMillis(200),Duration.ofSeconds(3));
            }
            try(var recovered=pool.getConnection()){assertThat(recovered.isValid(1)).isTrue();}
        }
    }
}
