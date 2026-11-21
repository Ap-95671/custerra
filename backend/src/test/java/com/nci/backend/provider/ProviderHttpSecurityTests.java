package com.nci.backend.provider;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProviderHttpSecurityTests {
    @Test void publicProviderRequestsIdentifyTheApplication() {
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://provider.example/data"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("User-Agent",
                "Custerra/0.1 (+https://github.com/Ap-95671/custerra)"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                "ok", org.springframework.http.MediaType.TEXT_PLAIN));
        assertThat(ProviderHttp.get(builder.build(), java.net.URI.create("https://provider.example/data"), "text/plain", 10))
            .isEqualTo("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        server.verify();
    }
    @Test void providerGetCannotFollowRedirectToAnotherHost() throws Exception {
        var connection = (java.net.HttpURLConnection) java.net.URI.create("https://provider.example/data").toURL().openConnection();
        new ProviderHttp.NoRedirectRequestFactory().prepareConnection(connection, "GET");
        assertThat(connection.getInstanceFollowRedirects()).isFalse();
        assertThat(connection.getRequestMethod()).isEqualTo("GET");
    }
}
