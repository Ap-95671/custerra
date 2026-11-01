package com.nci.backend.provider;

import java.net.URI;
import java.net.SocketTimeoutException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

/** Bounded transport. Never propagate exception messages or URLs: FIRMS credentials live in its path. */
public final class ProviderHttp {
    private ProviderHttp() {}
    public static RestClient client(int connectMs, int readMs) {
        var factory = new NoRedirectRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return RestClient.builder().requestFactory(factory).build();
    }
    static class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override protected void prepareConnection(java.net.HttpURLConnection connection, String method) throws java.io.IOException {
            super.prepareConnection(connection, method);
            connection.setInstanceFollowRedirects(false);
        }
    }
    public static byte[] get(RestClient client, URI uri, String accept, int maxBytes) {
        try {
            return client.get().uri(uri).header("Accept", accept)
                .header("User-Agent", "Custerra/0.1 (+https://github.com/Ap-95671/custerra)").exchange((request, response) -> {
                if (response.getStatusCode().value() != 200) throw new ProviderException(false);
                var bytes = response.getBody().readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) throw new ProviderException(false);
                return bytes;
            });
        } catch (ProviderException e) { throw e; }
        catch (ResourceAccessException e) {
            throw new ProviderException(e.getMostSpecificCause() instanceof SocketTimeoutException);
        } catch (RuntimeException e) { throw new ProviderException(false); }
    }
}
