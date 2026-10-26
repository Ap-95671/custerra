package com.nci.backend.auth;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="CUSTERRA_DATABASE_TESTS",matches="true")
class AccountSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired AccountRepository accounts;
    @Autowired com.nci.backend.monitoring.service.JourneyNoticeService notices;
    @Autowired com.nci.backend.alert.repository.PushSubscriptionRepository subscriptions;
    private static final String PASSWORD="Fixture-password-2026";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));
        p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));
        p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));
    }
    private String username() { return "fixture_"+UUID.randomUUID().toString().replace("-","").substring(0,16); }
    private MockHttpSession login(String name) throws Exception {
        accounts.create(name,passwords.encode(PASSWORD));
        var result=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession();
        String oldId=session.getId();
        var token=json.readTree(result.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token)
                .param("username",name.toUpperCase(Locale.ROOT)).param("password",PASSWORD))
                .andExpect(status().isNoContent());
        assertThat(session.getId()).isNotEqualTo(oldId);
        return session;
    }
    private UUID journey(MockHttpSession session) throws Exception {
        var result=mvc.perform(post("/api/v1/journeys").session(session).with(csrf()).contentType("application/json").content("""
                {"name":"Private journey","route":{"points":[{"latitude":0,"longitude":0},{"latitude":0,"longitude":1}],"corridorKm":25,"days":7,"minMagnitude":4}}
                """)).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
    @Test void registrationHashesPasswordsAndDoesNotAuthenticateImplicitly() throws Exception {
        String name=username();
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",name,"password",PASSWORD))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        String hash=accounts.find(name).orElseThrow().passwordHash();
        assertThat(hash).startsWith("$2a$12$").isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD,hash)).isTrue();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",name.toUpperCase(Locale.ROOT),"password",PASSWORD)))).andExpect(status().isConflict());
    }
    @Test void rejectsShortAndOverlongUtf8PasswordsAndMissingCsrf() throws Exception {
        for(String password:List.of("short", "é".repeat(40))) mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",username(),"password",password)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/v1/auth/login").param("username",username()).param("password",PASSWORD)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username",username()).param("password","a".repeat(73))).andExpect(status().isUnauthorized());
    }
    @Test void sessionsRotateLoginAndLogoutInvalidatesAccess() throws Exception {
        String name=username(); var session=login(name);
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(name));
        mvc.perform(post("/api/v1/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/journeys").cookie(new jakarta.servlet.http.Cookie("JSESSIONID",session.getId())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }
    @Test void allJourneyActionsEnforceOwnerAndDoNotLeakGuessedIds() throws Exception {
        var owner=login(username()); var stranger=login(username()); var id=journey(owner);
        mvc.perform(get("/api/v1/journeys").session(stranger)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/journeys/"+id).session(stranger)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/journeys/"+id+"/notices").session(stranger)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/journeys/"+id).session(stranger).with(csrf()).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/journeys/"+id+"/check").session(stranger).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/journeys/"+id).session(stranger).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/journeys/"+id).session(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(delete("/api/v1/journeys/"+id).session(owner).with(csrf())).andExpect(status().isNoContent());
    }
    @Test void unownedLegacyDataRemainsHiddenAndSnapshotsEnforceOwnership() throws Exception {
        String name=username();var session=login(name);var owner=accounts.find(name).orElseThrow().id();
        var id=UUID.randomUUID();
        var fixture=new com.nci.backend.history.HistorySnapshot(id,"fixture",new com.nci.backend.history.HistoryQuery(0,0,100,
                java.time.Instant.parse("2025-01-01T00:00:00Z"),java.time.Instant.parse("2026-01-01T00:00:00Z"),4),java.time.Instant.now(),0,1,0.,false,List.of(),null,"Fixture");
        jdbc.update("insert into history_snapshot(id,fetched_at,payload,owner_id) values (?,now(),?,?)",id,json.writeValueAsString(fixture),owner);
        mvc.perform(get("/api/v1/history/earthquakes/"+id).session(session)).andExpect(status().isOk());
        var stranger=login(username());
        mvc.perform(get("/api/v1/history/earthquakes/"+id).session(stranger)).andExpect(status().isNotFound());
        jdbc.update("update history_snapshot set owner_id=null where id=?",id);
        mvc.perform(get("/api/v1/history/earthquakes/"+id).session(session)).andExpect(status().isNotFound());
        var journeyId=journey(session);jdbc.update("update monitored_journey set owner_id=null where id=?",journeyId);
        mvc.perform(get("/api/v1/journeys/"+journeyId).session(session)).andExpect(status().isNotFound());
    }
    @Test void expiredSessionHasJsonUnauthorizedWithoutRedirectAndPrivateWritesRequireCsrf() throws Exception {
        var session=login(username());var id=journey(session);
        mvc.perform(delete("/api/v1/journeys/"+id).session(session)).andExpect(status().isForbidden());
        session.invalidate();
        mvc.perform(get("/api/v1/journeys").session(new MockHttpSession())).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location")).andExpect(jsonPath("$.path").value("/api/v1/journeys"));
    }
    @Test void corsAllowsCredentialedConfiguredOriginAndRejectsForeignOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin","http://localhost:3000")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","X-CSRF-TOKEN"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials","true"));
        mvc.perform(get("/api/v1/auth/csrf").header("Origin","https://untrusted.example"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    @Test void authenticationThrottleIsBoundedAndReturnsRetryAfter() throws Exception {
        for(int i=0;i<20;i++) mvc.perform(post("/api/v1/auth/login").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.44");return r;})
                .param("username","unknown_fixture").param("password","x".repeat(73))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.44");return r;})
                .param("username","unknown_fixture").param("password",PASSWORD)).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After","600"));
    }
    @Test void switchingAccountRevokesPreviousSessionsPushSubscription() throws Exception {
        String first=username(),second=username();var session=login(first);
        var owner=accounts.find(first).orElseThrow().id();
        accounts.create(second,passwords.encode(PASSWORD));
        var keys=com.nci.backend.alert.PushFixtures.keys();
        var subscription=subscriptions.save(owner,"https://fcm.googleapis.com/fcm/send/switch_fixture",keys.publicKey(),keys.auth());
        session.setAttribute(com.nci.backend.alert.repository.PushSubscriptionRepository.SESSION_ATTRIBUTE,subscription);
        mvc.perform(post("/api/v1/auth/login").session(session).with(csrf())
                .param("username",second).param("password",PASSWORD)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(second));
        assertThat(session.getAttribute(com.nci.backend.alert.repository.PushSubscriptionRepository.SESSION_ATTRIBUTE)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from push_subscription where id=?",Integer.class,subscription)).isZero();
    }
    @Test void notificationReadAndSubscriptionsRespectOwnerAndLogoutRevokesCurrentDevice() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        String name=username();var session=login(name);var stranger=login(username());var owner=accounts.find(name).orElseThrow().id();var journey=journey(session);
        var notice=new com.nci.backend.monitoring.dto.JourneyNotice(UUID.randomUUID(),journey,java.time.Instant.now(),"NEW_MATCHING_OBSERVATION",
                com.nci.backend.journey.FeatureFixtures.assessment(false,"security_fixture").matches().getFirst(),"Fixture notice",1);
        notices.create(notice);
        mvc.perform(get("/api/v1/notifications/"+notice.id()).session(stranger)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/notifications/"+notice.id()+"/read").session(stranger).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications/"+notice.id()).session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/notifications/"+notice.id()+"/read").session(session).with(csrf())).andExpect(status().isNoContent());
        var keys=com.nci.backend.alert.PushFixtures.keys();var subscription=subscriptions.save(owner,"https://fcm.googleapis.com/fcm/send/security_fixture",keys.publicKey(),keys.auth());
        session.setAttribute(com.nci.backend.alert.repository.PushSubscriptionRepository.SESSION_ATTRIBUTE,subscription);
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from push_subscription where id=?",Integer.class,subscription)).isZero();
    }

}
