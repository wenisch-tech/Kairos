package tech.wenisch.kairos.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private MonitoredResourceRepository resourceRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    private SimpleMeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry, resourceRepository, checkResultRepository);
    }

    @Test
    void registerResourceMetricPublishesAvailableStatus() {
        MonitoredResource resource = resource("Website", ResourceType.HTTP);
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource))
                .thenReturn(Optional.of(checkResult(resource, CheckStatus.AVAILABLE)));

        metricsService.registerResourceMetric(resource);

        assertThat(resourceGauge(resource).value()).isEqualTo(1.0);
    }

    @Test
    void registerResourceMetricPublishesUnknownWhenNoCheckExists() {
        MonitoredResource resource = resource("Statuspage", ResourceType.HTTP);
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource))
                .thenReturn(Optional.empty());

        metricsService.registerResourceMetric(resource);

        assertThat(resourceGauge(resource).value()).isEqualTo(-1.0);
    }

    @Test
    void registerResourceMetricKeepsStrongReferenceForGaugeStateObject() {
        MonitoredResource resource = resource("Kairos Dockerimage", ResourceType.DOCKER);
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(any(MonitoredResource.class)))
                .thenReturn(Optional.of(checkResult(resource, CheckStatus.NOT_AVAILABLE)));
        WeakReference<MonitoredResource> weakReference = new WeakReference<>(resource);

        metricsService.registerResourceMetric(resource);
        resource = null;

        forceGarbageCollection();

        assertThat(weakReference.get()).isNotNull();
        assertThat(resourceGauge("Kairos Dockerimage", ResourceType.DOCKER).value()).isEqualTo(0.0);
    }

    private Gauge resourceGauge(MonitoredResource resource) {
        return resourceGauge(resource.getName(), resource.getResourceType());
    }

    private Gauge resourceGauge(String resourceName, ResourceType resourceType) {
        return meterRegistry.find("kairos_resource_status")
                .tag("resource_name", resourceName)
                .tag("resource_type", resourceType.name())
                .gauge();
    }

    private MonitoredResource resource(String name, ResourceType type) {
        return MonitoredResource.builder()
                .id(1L)
                .name(name)
                .resourceType(type)
                .target("target")
                .build();
    }

    private CheckResult checkResult(MonitoredResource resource, CheckStatus status) {
        return CheckResult.builder()
                .resource(resource)
                .status(status)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    private void forceGarbageCollection() {
        for (int i = 0; i < 5; i++) {
            System.gc();
            System.runFinalization();
        }
    }
}
