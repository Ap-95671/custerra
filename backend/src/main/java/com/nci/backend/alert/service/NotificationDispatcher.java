package com.nci.backend.alert.service;
import java.time.*;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import com.nci.backend.alert.repository.*;
import com.nci.backend.alert.provider.PushGateway;
@Service
public class NotificationDispatcher {
    private final DeliveryRepository deliveries;private final AlertRepository alerts;private final PushSubscriptionRepository subscriptions;
    private final PushGateway push;private final TransactionTemplate tx;private final Clock clock;private final ObjectMapper json;
    public NotificationDispatcher(DeliveryRepository deliveries,AlertRepository alerts,PushSubscriptionRepository subscriptions,PushGateway push,TransactionTemplate tx,Clock clock,ObjectMapper json) {
        this.deliveries=deliveries;this.alerts=alerts;this.subscriptions=subscriptions;this.push=push;this.tx=tx;this.clock=clock;this.json=json;
    }
    public void dispatch() {
        tx.executeWithoutResult(status->deliveries.recover(clock.instant()));
        if(!push.configured()) return;
        for(int i=0;i<10;i++) {
            var next=tx.execute(status->deliveries.claim(clock.instant()));
            if(next==null || next.isEmpty()) return;
            var job=next.get();var preference=alerts.preferences(job.subscription().owner());var event=job.notification().notice().evidence().event();
            PushGateway.Result result;
            if(!preference.pushEnabled() || !preference.accepts(event.hazardType(),event.severity())) result=new PushGateway.Result("CANCELLED","PREFERENCE_DISABLED");
            else if(job.notification().notice().createdAt().isBefore(clock.instant().minus(Duration.ofHours(24)))) result=new PushGateway.Result("CANCELLED","NOTICE_TOO_OLD");
            else {
                String text=event.hazardType()+" · "+event.informationType()+" · "+event.source()+" · "+job.notification().notice().type()+" · "+job.notification().notice().createdAt()+". Review current evidence; this is informational, not an official warning.";
                String payload=json.writeValueAsString(Map.of("id",job.notification().id().toString(),"title","Custerra · "+job.notification().journeyName(),"body",text,"url","/alerts?notice="+job.notification().id()));
                try { result=push.send(job.subscription(),payload,job.notification().id().toString().replace("-","")); }
                catch(RuntimeException failure) { result=new PushGateway.Result("UNCERTAIN","DELIVERY_OUTCOME_UNKNOWN"); }
            }
            var outcome=result;
            tx.executeWithoutResult(status->{
                String state=outcome.outcome().equals("RETRY") ? (job.attempt()<3?"PENDING":"FAILED") : outcome.outcome().equals("EXPIRED")?"FAILED":outcome.outcome();
                deliveries.finish(job,state,outcome.error(),clock.instant());
                if(outcome.outcome().equals("EXPIRED")) subscriptions.expire(job.subscription().id());
            });
            if(Thread.currentThread().isInterrupted()) return;
        }
    }
}
