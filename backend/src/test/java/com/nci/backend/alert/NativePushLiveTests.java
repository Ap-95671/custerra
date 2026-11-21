package com.nci.backend.alert;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import com.nci.backend.alert.provider.WebPushGateway;
import com.nci.backend.alert.repository.PushSubscriptionRepository.Subscription;
import static org.assertj.core.api.Assertions.*;
/** Operator-authorized direct gateway test; never selects a non-test account or remote database. */
@EnabledIfEnvironmentVariable(named="CUSTERRA_NATIVE_PUSH_AUTHORIZED",matches="true")
class NativePushLiveTests {
 @Test void oneHarmlessMessageToNamedLocalTestAccount()throws Exception{
  String url=System.getenv("TEST_DB_URL"),username=System.getenv("CUSTERRA_NATIVE_TEST_USERNAME");
  assertThat(url).matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/custerra_[a-zA-Z0-9_]+_test");assertThat(username).matches("v3_native_[a-f0-9]{10}");
  var subscriptions=new ArrayList<Subscription>();
  try(var connection=DriverManager.getConnection(url,System.getenv("TEST_DB_USERNAME"),System.getenv("TEST_DB_PASSWORD"));var query=connection.prepareStatement("select s.id,s.owner_id,s.endpoint,s.p256dh,s.auth_secret from push_subscription s join user_account u on u.id=s.owner_id where u.username=?")){
   query.setString(1,username);try(var result=query.executeQuery()){while(result.next())subscriptions.add(new Subscription(result.getObject(1,UUID.class),result.getObject(2,UUID.class),result.getString(3),result.getString(4),result.getString(5)));}
  }
  assertThat(subscriptions).as("Exactly one explicitly enabled browser in the named disposable account").hasSize(1);
  var gateway=new WebPushGateway(System.getenv("VAPID_PUBLIC_KEY"),System.getenv("VAPID_PRIVATE_KEY"),System.getenv("VAPID_SUBJECT"));assertThat(gateway.configured()).isTrue();
  UUID id=UUID.randomUUID();var at=Instant.now();var json=new JsonMapper();var payload=Map.of("id",id.toString(),"title","Custerra TEST notification","body","No action required. This is a delivery test and is not a disaster warning.","url","/alerts");
  var result=gateway.send(subscriptions.getFirst(),json.writeValueAsString(payload),id.toString().replace("-",""));
  Files.writeString(Path.of("target/native-push-result.json"),json.writeValueAsString(Map.of("id",id,"attemptedAt",at,"gatewayOutcome",result.outcome(),"error",result.error()==null?"":result.error(),"title",payload.get("title"),"scope","DIRECT_GATEWAY_LOCAL_TEST_ACCOUNT_ONLY","nativeDisplay","REQUIRES_SEPARATE_DEVICE_OBSERVATION")));
  System.out.println("Native Chrome test gateway outcome="+result.outcome()+", test message id="+id+". Native display requires separate observation.");
  assertThat(result.outcome()).isEqualTo("SENT");
 }
}
