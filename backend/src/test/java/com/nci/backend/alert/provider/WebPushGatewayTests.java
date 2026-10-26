package com.nci.backend.alert.provider;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.nci.backend.alert.PushFixtures;
import com.nci.backend.alert.repository.PushSubscriptionRepository.Subscription;
import static org.assertj.core.api.Assertions.*;
class WebPushGatewayTests {
    @Test void sdkProducesEncryptedPayloadVapidAndBoundedTtlWithoutNetwork() throws Exception {
        var server=PushFixtures.keys();var browser=PushFixtures.keys();var gateway=new WebPushGateway(server.publicKey(),server.privateKey(),"mailto:fixture@example.test");
        var request=gateway.prepare(new Subscription(UUID.randomUUID(),UUID.randomUUID(),"https://fcm.googleapis.com/fcm/send/fixture",browser.publicKey(),browser.auth()),"private fixture body","fixturetopic");
        assertThat(gateway.configured()).isTrue();assertThat(request.getHeaders()).containsEntry("TTL","3600").containsEntry("Content-Encoding","aes128gcm").containsEntry("Topic","fixturetopic");
        assertThat(request.getHeaders().get("Authorization")).startsWith("vapid ");
        assertThat(new String(request.getBody(),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("private fixture body");
        assertThat(request.getBody().length).isGreaterThan(50);
    }
    @Test void emptyConfigurationDisablesPushAndMismatchedKeysFailFast() {
        assertThat(new WebPushGateway("","","").configured()).isFalse();
        var a=PushFixtures.keys();var b=PushFixtures.keys();
        assertThatThrownBy(()->new WebPushGateway(a.publicKey(),b.privateKey(),"mailto:fixture@example.test")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void endpointAllowlistRejectsCredentialRedirectAndLocalTargets() {
        for(String endpoint:List.of("http://fcm.googleapis.com/push","https://fcm.googleapis.com.attacker.test/push","https://user:pass@fcm.googleapis.com/push","https://fcm.googleapis.com:443/push","https://localhost/push","https://fcm.googleapis.com/push#fragment"))
            assertThatThrownBy(()->PushSubscriptionValidator.validateEndpoint(endpoint)).isInstanceOf(IllegalArgumentException.class);
        PushSubscriptionValidator.validateEndpoint("https://updates.push.services.mozilla.com/wpush/v2/fixture");
    }
    @Test void transportClassifiesRejectionsAndTimeoutWithoutExposingEndpoints() throws Exception {
        var server=PushFixtures.keys();var browser=PushFixtures.keys();
        var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        @SuppressWarnings("unchecked") java.net.http.HttpResponse<Void> response=org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<Void>>any())).thenReturn(response);
        var gateway=new WebPushGateway(server.publicKey(),server.privateKey(),"mailto:fixture@example.test",http);
        var subscription=new Subscription(UUID.randomUUID(),UUID.randomUUID(),"https://fcm.googleapis.com/fcm/send/fixture",browser.publicKey(),browser.auth());
        int[] statuses={201,410,429,503,400,302};String[] outcomes={"SENT","EXPIRED","RETRY","RETRY","FAILED","FAILED"};
        for(int i=0;i<statuses.length;i++) {org.mockito.Mockito.when(response.statusCode()).thenReturn(statuses[i]);assertThat(gateway.send(subscription,"fixture","topic").outcome()).isEqualTo(outcomes[i]);}
        org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<Void>>any())).thenThrow(new java.net.http.HttpTimeoutException("Sensitive endpoint should not be exposed"));
        assertThat(gateway.send(subscription,"fixture","topic")).isEqualTo(new PushGateway.Result("UNCERTAIN","DELIVERY_OUTCOME_UNKNOWN"));
    }

}
