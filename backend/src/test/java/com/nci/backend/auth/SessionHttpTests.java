package com.nci.backend.auth;

import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="CUSTERRA_DATABASE_TESTS",matches="true")
class SessionHttpTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));
        p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));
        p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));
    }
    private HttpResponse<String> send(HttpClient client,String path,String method,String body,String token,String contentType) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/"+path))
                .header("Origin","http://localhost:3000").timeout(java.time.Duration.ofSeconds(15));
        if(token!=null) request.header("X-CSRF-TOKEN",token);
        if(contentType!=null) request.header("Content-Type",contentType);
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void realHttpCookieSessionUsesHttpOnlySameSiteAndFreshCsrfAfterLogin() throws Exception {
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        try(var client=HttpClient.newBuilder().cookieHandler(cookies).build()) {
            String name="http_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
            try {
                var csrf=send(client,"auth/csrf","GET",null,null,null);
                assertThat(csrf.statusCode()).isEqualTo(200);
                assertThat(csrf.headers().firstValue("set-cookie").orElseThrow()).contains("HttpOnly","SameSite=Lax");
                assertThat(csrf.headers().firstValue("cache-control").orElseThrow()).contains("no-store");
                String token=json.readTree(csrf.body()).get("token").asText();
                var registration=send(client,"auth/register","POST",json.writeValueAsString(Map.of("username",name,"password","HTTP-fixture-password")),token,"application/json");
                assertThat(registration.statusCode()).isEqualTo(201);
                String beforeLogin=cookies.getCookieStore().getCookies().stream().filter(cookie->cookie.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
                var login=send(client,"auth/login","POST","username="+name+"&password=HTTP-fixture-password",token,"application/x-www-form-urlencoded");
                assertThat(login.statusCode()).isEqualTo(204);
                assertThat(cookies.getCookieStore().getCookies().stream().filter(cookie->cookie.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue()).isNotEqualTo(beforeLogin);
                assertThat(send(client,"auth/me","GET",null,null,null).statusCode()).isEqualTo(200);
                assertThat(send(client,"auth/logout","POST",null,token,null).statusCode()).isEqualTo(403);
                String fresh=json.readTree(send(client,"auth/csrf","GET",null,null,null).body()).get("token").asText();
                assertThat(send(client,"auth/logout","POST",null,fresh,null).statusCode()).isEqualTo(204);
                assertThat(send(client,"auth/me","GET",null,null,null).statusCode()).isEqualTo(401);
            } finally { jdbc.update("delete from user_account where username=?",name); }
        }
    }
}
