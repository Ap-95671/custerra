package com.nci.backend.journey;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nci.backend.monitoring.dto.*;
import com.nci.backend.monitoring.service.TripMonitoringService;
import com.nci.backend.monitoring.repository.MonitoredTripRepository;
import com.nci.backend.route.service.RouteRiskService;
import com.nci.backend.history.*;
import com.nci.backend.provider.ProviderException;
import com.nci.backend.provider.model.*;
import com.nci.backend.route.dto.RouteRiskResponse;
import com.nci.backend.hazard.model.*;
import static com.nci.backend.journey.FeatureFixtures.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="CUSTERRA_DATABASE_TESTS",matches="true")
class JourneyPersistenceTests {
    @Autowired TripMonitoringService service;
    @Autowired MonitoredTripRepository repository;
    @Autowired HistoryRepository histories;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean RouteRiskService risk;
    @MockitoBean com.nci.backend.auth.CurrentAccount current;
    private static final UUID OWNER=UUID.fromString("88888888-8888-4888-8888-888888888888");
    @BeforeEach void account() {
        jdbc.update("insert into user_account(id,username,password_hash) values (?,'journey_fixture','unusable') on conflict do nothing",OWNER);
        when(current.id()).thenReturn(OWNER);
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));
        properties.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));
        properties.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));
    }
    private UUID active() {
        var saved = service.create(new MonitoringRequest("Test journey",ROUTE));
        assertThat(saved.status()).isEqualTo("PAUSED");
        return service.status(saved.id(),"ACTIVE").id();
    }
    @Test void baselineThenNewMatchingEventsProduceOneDurableNotice() {
        var id = active();
        when(risk.assessMonitored(ROUTE)).thenReturn(assessment(false,"old"),assessment(false,"old","new"),assessment(false,"new","old"));
        service.check(id); assertThat(service.notices(id,0)).isEmpty();
        service.check(id); service.check(id);
        assertThat(service.notices(id,0)).hasSize(1);
        assertThat(service.notices(id,0).getFirst().evidence().event().externalId()).isEqualTo("new");
        assertThat(service.get(id).assessment().matches()).hasSize(2);
        assertThat(service.get(id).route()).isEqualTo(ROUTE);
    }
    @Test void incompleteAndFailedChecksPreserveBaselineAndDoNotConsumeUnseenEvents() {
        var id = active();
        when(risk.assessMonitored(ROUTE)).thenReturn(assessment(false,"old"),assessment(true,"new"))
                .thenThrow(new ProviderException(true)).thenReturn(assessment(false,"old","new"));
        var baseline = service.check(id).lastCheckedAt();
        assertThat(service.check(id).lastError()).isEqualTo("INCOMPLETE_COVERAGE");
        assertThat(service.check(id).lastError()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(service.get(id).lastCheckedAt()).isEqualTo(baseline);
        assertThat(service.notices(id,0)).isEmpty();
        assertThat(service.check(id).lastError()).isNull();
        assertThat(service.notices(id,0)).hasSize(1);
    }
    @Test void pausedChecksFailAndDeletionCascadesEvidence() {
        var id = active(); when(risk.assessMonitored(ROUTE)).thenReturn(assessment(false,"old"),assessment(false,"new"));
        service.check(id);service.check(id);service.status(id,"PAUSED");
        assertThatThrownBy(()->service.check(id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        service.delete(id);
        assertThat(jdbc.queryForObject("select count(*) from journey_seen where journey_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from journey_notice where journey_id=?",Integer.class,id)).isZero();
        assertThatThrownBy(()->service.get(id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void schedulerDueGuardSkipsFreshChecksAndPausedJourneys() {
        var id = active(); when(risk.assessMonitored(ROUTE)).thenReturn(assessment(false));
        service.check(id); service.check(id,NOW);
        verify(risk,times(1)).assessMonitored(ROUTE);
        assertThat(repository.due(NOW)).doesNotContain(id);
        service.status(id,"PAUSED");
        assertThat(repository.due(Instant.now().plusSeconds(1000))).doesNotContain(id);
    }
    @Test void immutableHistoricalSnapshotRetainsQueryEvidenceAndVersion() {
        var query = new HistoryQuery(0,0,100,NOW.minusSeconds(86400),NOW,4);
        var snapshot = new HistorySnapshot(UUID.randomUUID(),"catalog-frequency-v1",query,NOW,1,1/365.2425,365.2425,false,
                List.of(event("stored",0,0,4,NOW.minusSeconds(1))),new ProviderMetadata("USGS","USGS","https://example.test","https://example.test","Test"),"Test");
        histories.save(snapshot);
        assertThat(histories.find(snapshot.id()).orElseThrow()).isEqualTo(snapshot);
        assertThat(histories.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentChecksCannotDuplicateNotices() throws Exception {
        var id = active();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            when(risk.assessMonitored(ROUTE)).thenReturn(assessment(false, "baseline"));
            service.check(id);
            when(risk.assessMonitored(ROUTE)).thenReturn(assessment(false, "baseline", "new"));
            var first = workers.submit(() -> service.check(id));
            var second = workers.submit(() -> service.check(id));
            first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            second.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(service.notices(id,0)).hasSize(1);
        } finally { service.delete(id); }
    }
    private NormalizedHazardEvent advisory(String id, SeverityLevel severity, Instant updated) {
        return new NormalizedHazardEvent(id,HazardType.FLOOD,"Provider advisory",severity,InformationType.ADVISORY,
                0,1,null,NOW.minusSeconds(1000),null,"GDACS","https://www.gdacs.org/",NOW,updated,ConfidenceLevel.UNKNOWN,Map.of());
    }
    private RouteRiskResponse multi(CoverageStatus usgs, CoverageStatus gdacs, NormalizedHazardEvent... events) {
        return new RouteRiskResponse("route-proximity-v2-multi-hazard",ROUTE,NOW,222,"UNKNOWN",false,
                Arrays.stream(events).map(e -> new RouteRiskResponse.Match(e,0,0)).toList(),List.of(),"Test evidence",
                usgs == CoverageStatus.COMPLETE && gdacs == CoverageStatus.COMPLETE ? CoverageStatus.COMPLETE : CoverageStatus.PARTIAL,
                List.of(new ProviderStatus("USGS",usgs,NOW,List.of()),new ProviderStatus("GDACS",gdacs,NOW,List.of())));
    }
    @Test void providerFailureDoesNotConsumeOtherProvidersBaselineOrUnseenEvidence() {
        var id=active(); var first=event("first",0,1,4,NOW); var next=event("second",0,1,4,NOW);
        var flood=advisory("flood",SeverityLevel.LOW,NOW);
        when(risk.assessMonitored(ROUTE)).thenReturn(
                multi(CoverageStatus.COMPLETE,CoverageStatus.UNAVAILABLE,first),
                multi(CoverageStatus.COMPLETE,CoverageStatus.PARTIAL,first,next,flood),
                multi(CoverageStatus.UNAVAILABLE,CoverageStatus.PARTIAL,advisory("flood",SeverityLevel.HIGH,NOW.plusSeconds(1))));
        assertThat(service.check(id).providerCheckpoints()).extracting(MonitoringResponse.ProviderCheckpoint::provider).containsExactly("USGS");
        service.check(id); service.check(id);
        assertThat(service.notices(id,0)).extracting(JourneyNotice::type).containsExactlyInAnyOrder("NEW_MATCHING_OBSERVATION","UPDATED_MATCHING_EVIDENCE");
        assertThat(service.get(id).lastCheckedAt()).isNull();
        assertThat(service.get(id).providerCheckpoints()).hasSize(2);
    }
    @Test void revisionDedupIgnoresRetrievalOnlyChangesAndRejectsOlderRevisions() {
        var id=active();
        when(risk.assessMonitored(ROUTE)).thenReturn(
                multi(CoverageStatus.COMPLETE,CoverageStatus.PARTIAL,advisory("flood",SeverityLevel.LOW,NOW)),
                multi(CoverageStatus.COMPLETE,CoverageStatus.PARTIAL,advisory("flood",SeverityLevel.LOW,NOW.plusSeconds(1))),
                multi(CoverageStatus.COMPLETE,CoverageStatus.PARTIAL,advisory("flood",SeverityLevel.HIGH,NOW.plusSeconds(2))),
                multi(CoverageStatus.COMPLETE,CoverageStatus.PARTIAL,advisory("flood",SeverityLevel.LOW,NOW)),
                multi(CoverageStatus.COMPLETE,CoverageStatus.PARTIAL,advisory("flood",SeverityLevel.HIGH,NOW.plusSeconds(3))));
        for(int i=0;i<5;i++) service.check(id);
        assertThat(service.notices(id,0)).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo("UPDATED_MATCHING_EVIDENCE");
            assertThat(n.evidence().event().severity()).isEqualTo(SeverityLevel.HIGH);
        });
    }
    @Test void partialAbsenceDoesNotResolveEvidenceAndCompleteAbsenceIsDeduplicated() {
        var id=active(); var flood=advisory("flood",SeverityLevel.LOW,NOW);
        when(risk.assessMonitored(ROUTE)).thenReturn(
                multi(CoverageStatus.COMPLETE,CoverageStatus.PARTIAL,flood),
                multi(CoverageStatus.COMPLETE,CoverageStatus.PARTIAL),
                multi(CoverageStatus.COMPLETE,CoverageStatus.TRUNCATED),
                multi(CoverageStatus.COMPLETE,CoverageStatus.COMPLETE),
                multi(CoverageStatus.COMPLETE,CoverageStatus.COMPLETE),
                multi(CoverageStatus.COMPLETE,CoverageStatus.COMPLETE,flood));
        service.check(id);service.check(id);service.check(id);
        assertThat(service.notices(id,0)).isEmpty();
        service.check(id);service.check(id);service.check(id);
        assertThat(service.notices(id,0)).extracting(JourneyNotice::type).containsExactlyInAnyOrder("NO_LONGER_MATCHING","MATCHING_EVIDENCE_RETURNED");
        service.delete(id);
        assertThat(jdbc.queryForObject("select count(*) from journey_provider_checkpoint where journey_id=?",Integer.class,id)).isZero();
    }
    @Test void legacySeenIdentitiesAreAdoptedWithoutSpuriousUpdate() {
        var id=active();
        jdbc.update("insert into journey_seen(journey_id,source,external_id) values (?,'USGS','legacy')",id);
        repository.checkpoint(id,"USGS",NOW);
        when(risk.assessMonitored(ROUTE)).thenReturn(assessment(false,"legacy"));
        service.check(id);service.check(id);
        assertThat(service.notices(id,0)).isEmpty();
    }

}
