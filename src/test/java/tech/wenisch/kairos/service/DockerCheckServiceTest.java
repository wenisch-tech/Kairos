package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.AuthType;
import tech.wenisch.kairos.entity.ResourceTypeAuth;
import tech.wenisch.kairos.repository.CheckResultRepository;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DockerCheckServiceTest {

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private AuthService authService;

    @Mock
    private ResourceStatusStreamService resourceStatusStreamService;

    @Mock
    private OutageService outageService;

    @Mock
    private ProxySettingsService proxySettingsService;

    @Mock
    private MetricsService metricsService;

    @Mock
    private ApplicationTimeService applicationTimeService;

    private DockerCheckService service;

    @BeforeEach
    void setUp() {
        service = new DockerCheckService(
                checkResultRepository,
                authService,
                resourceStatusStreamService,
                outageService,
                proxySettingsService,
                metricsService,
                applicationTimeService
        );
    }

    @Test
    void bearerResourceAuthCreatesBearerAuthState() throws Exception {
        ResourceTypeAuth auth = ResourceTypeAuth.builder()
                .authType(AuthType.BEARER)
                .password(" token-123 ")
                .build();

        Object authState = toAuthState(auth);

        assertThat(invokeString(authState, "getBasicAuthHeader")).isNull();
        assertThat(invokeString(authState, "getBearerToken")).isEqualTo("token-123");
    }

    @Test
    void basicResourceAuthCreatesBasicAuthState() throws Exception {
        ResourceTypeAuth auth = ResourceTypeAuth.builder()
                .authType(AuthType.BASIC)
                .username("user")
                .password("pass")
                .build();

        Object authState = toAuthState(auth);

        assertThat(invokeString(authState, "getBasicAuthHeader")).isEqualTo("Basic dXNlcjpwYXNz");
        assertThat(invokeString(authState, "getBearerToken")).isNull();
    }

    private Object toAuthState(ResourceTypeAuth auth) throws Exception {
        Method method = DockerCheckService.class.getDeclaredMethod("toAuthState", ResourceTypeAuth.class);
        method.setAccessible(true);
        return method.invoke(service, auth);
    }

    private String invokeString(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (String) method.invoke(target);
    }
}