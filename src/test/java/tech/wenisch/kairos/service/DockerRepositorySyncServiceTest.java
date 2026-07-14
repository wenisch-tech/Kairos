package tech.wenisch.kairos.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.AuthType;
import tech.wenisch.kairos.entity.DiscoveryServiceAuth;
import tech.wenisch.kairos.entity.ResourceDiscovery;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerRepositorySyncServiceTest {

    @Mock
    private ResourceService resourceService;

    @Mock
    private ResourceDiscoveryManagementService resourceDiscoveryManagementService;

    @Mock
    private MonitoredResourceRepository resourceRepository;

    @Mock
    private DockerCheckService dockerCheckService;

    @Mock
    private ResourceStatusStreamService resourceStatusStreamService;

    @Mock
    private ProxySettingsService proxySettingsService;

    private DockerRepositorySyncService service;

    @BeforeEach
    void setUp() {
        service = new DockerRepositorySyncService(
                resourceService,
                resourceDiscoveryManagementService,
                resourceRepository,
                dockerCheckService,
                resourceStatusStreamService,
                proxySettingsService
        );
    }

    @Test
    void extractsRepositoriesFromArtifactoryStorageManifestUris() throws Exception {
        Object repositoryRef = parseRepositoryRef("https://registry.example.com/artifactory/plain-images");
        Method extract = DockerRepositorySyncService.class.getDeclaredMethod(
                "extractRepositoryFromArtifactoryStorageUri",
                repositoryRef.getClass(),
                String.class,
                String.class
        );
        extract.setAccessible(true);

        assertThat(invokeExtract(extract, repositoryRef, "/alpine/3.23/manifest.json"))
                .contains("artifactory/plain-images/alpine");
        assertThat(invokeExtract(extract, repositoryRef, "/platform/tools/app/1.0.0/manifest.json"))
                .contains("artifactory/plain-images/platform/tools/app");
        assertThat(invokeExtract(extract, repositoryRef,
                "/alpine/sha256__136d35617cfa35dd6eac07cdff9a1f96bad6267e8994b9a59a82ab365d8195b5/manifest.json"))
                .isEmpty();
        assertThat(invokeExtract(extract, repositoryRef, "/alpine/latest/sha256__layer"))
                .isEmpty();
    }

    @Test
    void sendGetUsesBearerAuthForDiscoveryCredentials() throws Exception {
        when(proxySettingsService.resolveHttpProxyForTarget(anyString())).thenReturn(Optional.empty());
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            DiscoveryServiceAuth auth = DiscoveryServiceAuth.builder()
                    .authType(AuthType.BEARER)
                    .password("token-123")
                    .build();
            Method sendGet = DockerRepositorySyncService.class.getDeclaredMethod(
                    "sendGet", String.class, Optional.class, ResourceDiscovery.class);
            sendGet.setAccessible(true);

            @SuppressWarnings("unchecked")
            HttpResponse<String> response = (HttpResponse<String>) sendGet.invoke(
                    service,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/catalog",
                    Optional.of(auth),
                    ResourceDiscovery.builder().build()
            );

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(authorizationHeader).hasValue("Bearer token-123");
        } finally {
            server.stop(0);
        }
    }

    private Object parseRepositoryRef(String target) throws Exception {
        Method parse = DockerRepositorySyncService.class.getDeclaredMethod("parseRepositoryRef", String.class);
        parse.setAccessible(true);
        return parse.invoke(service, target);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> invokeExtract(Method extract, Object repositoryRef, String uri) throws Exception {
        return (Optional<String>) extract.invoke(service, repositoryRef, "plain-images", uri);
    }
}