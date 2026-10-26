package com.nci.backend.alert.provider;
import com.nci.backend.alert.repository.PushSubscriptionRepository.Subscription;
public interface PushGateway {
    boolean configured();
    String publicKey();
    Result send(Subscription subscription,String payload,String topic);
    record Result(String outcome,String error) {
        public static Result sent() { return new Result("SENT",null); }
    }
}
