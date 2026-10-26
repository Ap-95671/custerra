package com.nci.backend.alert.provider;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.nci.backend.alert.repository.PushSubscriptionRepository.Subscription;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
/** Explicit opt-in; use only a test browser whose owner authorized a verification message. */
class LiveWebPushSmokeTests {
 @Test @EnabledIfEnvironmentVariable(named="CUSTERRA_WEBPUSH_SMOKE_FILE",matches=".+")
 void pushServiceAcceptsExplicitVerificationMessage() throws Exception {
  var values=new ObjectMapper().readTree(Files.readString(Path.of(System.getenv("CUSTERRA_WEBPUSH_SMOKE_FILE"))));
  var gateway=new WebPushGateway(System.getenv("VAPID_PUBLIC_KEY"),System.getenv("VAPID_PRIVATE_KEY"),System.getenv("VAPID_SUBJECT"));
  var subscription=new Subscription(UUID.randomUUID(),UUID.randomUUID(),values.get("endpoint").asText(),values.get("p256dh").asText(),values.get("auth").asText());
  UUID id=UUID.randomUUID();String payload=new ObjectMapper().writeValueAsString(java.util.Map.of("id",id.toString(),"title","Custerra verification — not a hazard alert","body","Authorized delivery test only. No hazard or travel advice is represented."));
  assertThat(gateway.send(subscription,payload,id.toString().replace("-",""))).isEqualTo(PushGateway.Result.sent());
 }
}
