package com.nci.backend.provider;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProviderHttpSecurityTests {
    @Test void providerGetCannotFollowRedirectToAnotherHost() throws Exception {
        var connection = (java.net.HttpURLConnection) java.net.URI.create("https://provider.example/data").toURL().openConnection();
        new ProviderHttp.NoRedirectRequestFactory().prepareConnection(connection, "GET");
        assertThat(connection.getInstanceFollowRedirects()).isFalse();
        assertThat(connection.getRequestMethod()).isEqualTo("GET");
    }
}
