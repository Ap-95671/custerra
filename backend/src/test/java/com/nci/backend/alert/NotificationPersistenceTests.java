package com.nci.backend.alert;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.nci.backend.alert.dto.*;
import com.nci.backend.alert.repository.*;
import com.nci.backend.alert.service.*;
import com.nci.backend.alert.provider.*;
import com.nci.backend.monitoring.dto.*;
import com.nci.backend.monitoring.service.*;
import com.nci.backend.auth.CurrentAccount;
import static com.nci.backend.journey.FeatureFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest
@EnabledIfEnvironmentVariable(named="CUSTERRA_DATABASE_TESTS",matches="true")
class NotificationPersistenceTests {
    @Autowired JdbcTemplate jdbc;@Autowired TripMonitoringService journeys;@Autowired JourneyNoticeService notices;
    @Autowired AlertRepository alerts;@Autowired PushSubscriptionRepository subscriptions;@Autowired DeliveryRepository deliveries;
    @Autowired NotificationDispatcher dispatcher;@Autowired TransactionTemplate tx;
    @MockitoBean CurrentAccount current;@MockitoBean PushGateway push;
    UUID owner,journey,subscription;JourneyNotice notice;
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));
    }
    @BeforeEach void setup() {
        owner=UUID.randomUUID();jdbc.update("insert into user_account(id,username,password_hash) values (?,?,?)",owner,"push_"+owner.toString().replace("-",""),"unusable");
        when(current.id()).thenReturn(owner);when(push.configured()).thenReturn(true);
        journey=journeys.create(new MonitoringRequest("Fixture journey",ROUTE)).id();
        var keys=PushFixtures.keys();subscription=subscriptions.save(owner,"https://fcm.googleapis.com/fcm/send/"+UUID.randomUUID(),keys.publicKey(),keys.auth());
        var defaults=NotificationPreference.defaults();alerts.preferences(owner,new NotificationPreference(true,true,defaults.hazardTypes(),defaults.minimumSeverity(),true));
        notice=new JourneyNotice(UUID.randomUUID(),journey,Instant.now(),"NEW_MATCHING_OBSERVATION",assessment(false,"fixture").matches().getFirst(),"Fixture only. Not an official warning.",1);
    }
    @AfterEach void cleanup() { jdbc.update("delete from monitored_journey where id=?",journey);jdbc.update("delete from user_account where id=?",owner); }
    @Test void noticeAndDeliveryAreAtomicDeduplicatedAndOwnerScoped() {
        notices.create(notice);notices.create(notice);
        assertThat(alerts.list(owner,0)).hasSize(1);assertThat(alerts.list(UUID.randomUUID(),0)).isEmpty();
        assertThat(alerts.find(UUID.randomUUID(),notice.id())).isEmpty();assertThat(alerts.markRead(UUID.randomUUID(),notice.id(),Instant.now())).isFalse();
        assertThat(alerts.markRead(owner,notice.id(),Instant.now())).isTrue();assertThat(alerts.find(owner,notice.id()).orElseThrow().readAt()).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where notification_id=?",Integer.class,notice.id())).isEqualTo(1);
        var rolled=new JourneyNotice(UUID.randomUUID(),journey,Instant.now(),"UPDATED_MATCHING_EVIDENCE",notice.evidence(),"Fixture",2);
        tx.executeWithoutResult(status->{notices.create(rolled);status.setRollbackOnly();});assertThat(alerts.find(owner,rolled.id())).isEmpty();
    }
    @Test void preferenceFilteringPreservesUnknownAndSuppressesOutboundUntilOptIn() {
        var p=NotificationPreference.defaults();alerts.preferences(owner,p);notices.create(notice);
        assertThat(alerts.find(owner,notice.id()).orElseThrow().deliveries()).isEmpty();
        alerts.preferences(owner,new NotificationPreference(true,true,p.hazardTypes(),com.nci.backend.hazard.model.SeverityLevel.LOW,false));
        var filtered=new JourneyNotice(UUID.randomUUID(),journey,Instant.now(),"UPDATED_MATCHING_EVIDENCE",notice.evidence(),"Fixture",2);notices.create(filtered);
        assertThat(alerts.find(owner,filtered.id())).isEmpty();
        assertThat(journeys.notices(journey,0)).hasSize(2);
    }
    @Test void acceptedDeliveryIsSentOnceAndAttemptsAreRecorded() {
        notices.create(notice);when(push.send(any(),anyString(),anyString())).thenReturn(PushGateway.Result.sent());dispatcher.dispatch();dispatcher.dispatch();
        verify(push,times(1)).send(any(),contains("Fixture journey"),eq(notice.id().toString().replace("-","")));
        assertThat(state()).isEqualTo("SENT");assertThat(attempts()).isEqualTo(1);
    }
    @Test void transientRejectionRetriesWithBoundedAttempts() {
        notices.create(notice);when(push.send(any(),anyString(),anyString())).thenReturn(new PushGateway.Result("RETRY","PUSH_REJECTED_TRANSIENT"));
        dispatcher.dispatch();assertThat(state()).isEqualTo("PENDING");dispatcher.dispatch();assertThat(attempts()).isEqualTo(1);
        for(int i=0;i<2;i++){jdbc.update("update notification_delivery set next_attempt_at=now()-interval '1 second' where notification_id=?",notice.id());dispatcher.dispatch();}
        assertThat(state()).isEqualTo("FAILED");assertThat(attempts()).isEqualTo(3);
    }
    @Test void ambiguousDeliveryIsNotRetriedAndExpiredSubscriptionRetainsAudit() {
        notices.create(notice);when(push.send(any(),anyString(),anyString())).thenReturn(new PushGateway.Result("UNCERTAIN","DELIVERY_OUTCOME_UNKNOWN"));dispatcher.dispatch();dispatcher.dispatch();
        assertThat(state()).isEqualTo("UNCERTAIN");verify(push,times(1)).send(any(),anyString(),anyString());
        var next=new JourneyNotice(UUID.randomUUID(),journey,Instant.now(),"UPDATED_MATCHING_EVIDENCE",notice.evidence(),"Fixture",2);notices.create(next);
        when(push.send(any(),anyString(),anyString())).thenReturn(new PushGateway.Result("EXPIRED","SUBSCRIPTION_EXPIRED"));dispatcher.dispatch();
        assertThat(alerts.find(owner,next.id()).orElseThrow().deliveries().getFirst().status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from push_subscription where id=?",Integer.class,subscription)).isZero();
        assertThat(attempts()).isEqualTo(1);
    }
    @Test void expiredWorkerLeaseBecomesUncertainWithoutResending() {
        notices.create(notice);tx.executeWithoutResult(status->deliveries.claim(Instant.now()));
        jdbc.update("update notification_delivery set lease_until=now()-interval '1 second' where notification_id=?",notice.id());dispatcher.dispatch();
        assertThat(state()).isEqualTo("UNCERTAIN");verify(push,never()).send(any(),anyString(),anyString());
    }
    @Test void disablingPreferenceCancelsAlreadyQueuedDelivery() {
        notices.create(notice);alerts.preferences(owner,NotificationPreference.defaults());dispatcher.dispatch();
        assertThat(state()).isEqualTo("CANCELLED");verify(push,never()).send(any(),anyString(),anyString());
    }
    @Test void concurrentDispatchersClaimOneMessageOnce() throws Exception {
        notices.create(notice);when(push.send(any(),anyString(),anyString())).thenAnswer(call->{Thread.sleep(100);return PushGateway.Result.sent();});
        try(var workers=Executors.newFixedThreadPool(2)) { var first=workers.submit(dispatcher::dispatch);var second=workers.submit(dispatcher::dispatch);first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS); }
        assertThat(state()).isEqualTo("SENT");verify(push,times(1)).send(any(),anyString(),anyString());
    }
    @Test void subscriptionOwnershipBoundsAndRemovalPreventQueuedDelivery() {
        var keys=PushFixtures.keys();
        assertThatThrownBy(()->subscriptions.save(owner,"https://127.0.0.1/private",keys.publicKey(),keys.auth())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->subscriptions.save(owner,"https://fcm.googleapis.com/fixture","bad","bad")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        notices.create(notice);subscriptions.remove(UUID.randomUUID(),subscription);assertThat(state()).isEqualTo("PENDING");
        subscriptions.remove(owner,subscription);assertThat(state()).isEqualTo("CANCELLED");dispatcher.dispatch();verify(push,never()).send(any(),anyString(),anyString());
    }
    private String state(){return jdbc.queryForObject("select status from notification_delivery where notification_id=?",String.class,notice.id());}
    private int attempts(){return jdbc.queryForObject("select count(*) from notification_attempt a join notification_delivery d on a.delivery_id=d.id where d.notification_id=?",Integer.class,notice.id());}
}
