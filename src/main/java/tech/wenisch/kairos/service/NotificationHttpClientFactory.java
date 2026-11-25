package tech.wenisch.kairos.service;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationProvider;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Builds the {@link RestTemplate} used by HTTP-based notification channels
 * (webhook, Discord, Mattermost, GitLab). TLS certificate and hostname errors are
 * ignored when the provider is configured to do so, or when the resource that
 * triggered the notification has "Ignore TLS" enabled.
 */
@Component
public class NotificationHttpClientFactory {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final RestTemplate secure = create(false);
    private final RestTemplate insecure = create(true);

    public boolean shouldIgnoreTls(NotificationProvider provider, MonitoredResource resource) {
        return Boolean.TRUE.equals(provider.getIgnoreTls()) || (resource != null && resource.isSkipTls());
    }

    public RestTemplate forProvider(NotificationProvider provider) {
        return forProvider(provider, null);
    }

    public RestTemplate forProvider(NotificationProvider provider, MonitoredResource resource) {
        return shouldIgnoreTls(provider, resource) ? insecure : secure;
    }

    private static RestTemplate create(boolean ignoreTls) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (ignoreTls) {
            try {
                TrustManager[] trustAll = {new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }};
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAll, new SecureRandom());
                SSLParameters params = new SSLParameters();
                params.setEndpointIdentificationAlgorithm("");
                builder.sslContext(sslContext).sslParameters(params);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Failed to initialize insecure HTTP client", ex);
            }
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(builder.build());
        factory.setReadTimeout(TIMEOUT);
        return new RestTemplate(factory);
    }
}
