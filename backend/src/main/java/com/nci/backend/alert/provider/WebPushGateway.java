package com.nci.backend.alert.provider;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import nl.martijndwars.webpush.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.nci.backend.alert.repository.PushSubscriptionRepository.Subscription;
@Component
public class WebPushGateway implements PushGateway {
    private final PreparedPush push;
    private final String publicKey;
    private final HttpClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public WebPushGateway(@Value("${custerra.notifications.push.public-key:}") String publicKey,
            @Value("${custerra.notifications.push.private-key:}") String privateKey,
            @Value("${custerra.notifications.push.subject:}") String subject) {
        this(publicKey,privateKey,subject,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    WebPushGateway(String publicKey,String privateKey,String subject,HttpClient http) {
        this.http=http;
        if(Security.getProvider("BC")==null) Security.addProvider(new BouncyCastleProvider());
        this.publicKey=publicKey;
        if(publicKey.isBlank() && privateKey.isBlank() && subject.isBlank()) { push=null;return; }
        try {
            var contact=URI.create(subject);
            if(publicKey.isBlank() || privateKey.isBlank() || !("mailto".equals(contact.getScheme()) || ("https".equals(contact.getScheme()) && contact.getHost()!=null)))
                throw new IllegalArgumentException();
            push=new PreparedPush(publicKey,privateKey,subject);
            var check=java.security.Signature.getInstance("SHA256withECDSA","BC");check.initSign(push.getPrivateKey());check.update(new byte[]{1,2,3});var signed=check.sign();
            check.initVerify(push.getPublicKey());check.update(new byte[]{1,2,3});if(!check.verify(signed)) throw new IllegalArgumentException();
        } catch(Exception invalid) { throw new IllegalArgumentException("Invalid Web Push configuration; supply a matching VAPID key pair and contact URI."); }
    }
    public boolean configured() { return push!=null; }
    public String publicKey() { return configured()?publicKey:null; }
    public Result send(Subscription subscription,String payload,String topic) {
        if(!configured()) return new Result("FAILED","PUSH_NOT_CONFIGURED");
        try {
            PushSubscriptionValidator.validateEndpoint(subscription.endpoint());
            if(payload.getBytes(StandardCharsets.UTF_8).length>3000) return new Result("FAILED","PAYLOAD_TOO_LARGE");
            var prepared=prepare(subscription,payload,topic);
            var request=java.net.http.HttpRequest.newBuilder(URI.create(prepared.getUrl())).timeout(Duration.ofSeconds(10));
            prepared.getHeaders().forEach(request::header);
            var response=http.send(request.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(prepared.getBody())).build(),HttpResponse.BodyHandlers.discarding());
            int status=response.statusCode();
            if(status>=200 && status<300) return Result.sent();
            if(status==404 || status==410) return new Result("EXPIRED","SUBSCRIPTION_EXPIRED");
            if(status==429 || status>=500) return new Result("RETRY","PUSH_REJECTED_TRANSIENT");
            return new Result("FAILED","PUSH_REJECTED");
        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt();return new Result("UNCERTAIN","DELIVERY_OUTCOME_UNKNOWN"); }
        catch(java.io.IOException transport) { return new Result("UNCERTAIN","DELIVERY_OUTCOME_UNKNOWN"); }
        catch(Exception invalid) { return new Result("FAILED","PUSH_REQUEST_INVALID"); }
    }
    nl.martijndwars.webpush.HttpRequest prepare(Subscription subscription,String payload,String topic) throws Exception {
        var notification=Notification.builder().endpoint(subscription.endpoint()).userPublicKey(subscription.p256dh())
                .userAuth(subscription.auth()).payload(payload).ttl(3600).topic(topic).urgency(Urgency.NORMAL).build();
        return push.prepare(notification);
    }
    private static class PreparedPush extends AbstractPushService<PreparedPush> {
        PreparedPush(String publicKey,String privateKey,String subject) throws java.security.GeneralSecurityException { super(publicKey,privateKey,subject); }
        nl.martijndwars.webpush.HttpRequest prepare(Notification notification) throws Exception { return prepareRequest(notification,Encoding.AES128GCM); }
    }

}
