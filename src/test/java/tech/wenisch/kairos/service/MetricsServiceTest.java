package tech.wenisch.kairos.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.OutageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private MonitoredResourceRepository resourceRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private OutageRepository outageRepository;

    private SimpleMeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry, resourceRepository, checkResultRepository, outageRepository);
    }

    @Test
    void registerResourceMetricPublishesAvailableStatus() {
        MonitoredResource resource = resource("Website", ResourceType.HTTP);

        metricsService.registerResourceMetric(resource);
        metricsService.recordCheckResult(checkResult(resource, CheckStatus.AVAILABLE));

        assertThat(resourceGauge(resource).value()).isEqualTo(1.0);
    }

    @Test
    void registerResourceMetricPublishesUnknownWhenNoCheckExists() {
        MonitoredResource resource = resource("Statuspage", ResourceType.HTTP);

        metricsService.registerResourceMetric(resource);

        assertThat(resourceGauge(resource).value()).isEqualTo(-1.0);
    }

    @Test
    void registerMetricsInitializesLatestCheckWithoutDereferencingCheckResultResource() {
        MonitoredResource resource = resource("Website", ResourceType.HTTP);
        CheckResult latest = CheckResult.builder()
                .status(CheckStatus.AVAILABLE)
                .checkedAt(LocalDateTime.now())
                .latencyMs(120L)
                .dnsResolutionMs(10L)
                .connectMs(20L)
                .tlsHandshakeMs(30L)
                .build();

        when(resourceRepository.findByActiveTrue()).thenReturn(List.of(resource));
        when(checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource)).thenReturn(Optional.of(latest));
        when(outageRepository.findAllActiveWithResource()).thenReturn(List.of());

        metricsService.registerMetrics();

        assertThat(resourceGauge(resource).value()).isEqualTo(1.0);
        assertThat(gauge("kairos_resource_last_check_latency_seconds", resource, "phase", "total").value())
                .isEqualTo(0.12);
    }

    @Test
    void registerResourceMetricKeepsStrongReferenceForGaugeStateObject() {
        MonitoredResource resource = resource("Kairos Dockerimage", ResourceType.DOCKER);

        metricsService.registerResourceMetric(resource);
        metricsService.recordCheckResult(checkResult(resource, CheckStatus.NOT_AVAILABLE));
        resource = null;

        forceGarbageCollection();

        assertThat(resourceGauge("Kairos Dockerimage", ResourceType.DOCKER).value()).isEqualTo(0.0);
    }

    @Test
    void recordCheckResultUpdatesLatencyGaugesCountersAndTimers() {
        MonitoredResource resource = resource("Website", ResourceType.HTTP);
        CheckResult result = CheckResult.builder()
                .resource(resource)
                .status(CheckStatus.AVAILABLE)
                .checkedAt(LocalDateTime.now())
                .errorCode("200")
                .latencyMs(120L)
                .dnsResolutionMs(10L)
                .connectMs(20L)
                .tlsHandshakeMs(30L)
                .build();

        metricsService.recordCheckResult(result);

        assertThat(gauge("kairos_resource_last_check_latency_seconds", resource, "phase", "total").value())
                .isEqualTo(0.12);
        assertThat(gauge("kairos_resource_last_check_latency_seconds", resource, "phase", "dns").value())
                .isEqualTo(0.01);
        assertThat(meterRegistry.find("kairos_resource_checks")
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .tag("status", "AVAILABLE")
                .tag("error_code", "200")
                .counter().count()).isEqualTo(1.0);
        assertThat(timer("kairos_resource_check_duration", resource, "status", "AVAILABLE")
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(120.0);
        assertThat(timer("kairos_resource_check_phase_duration", resource, "phase", "connect")
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(20.0);
    }

    @Test
    void recordCheckResultSkipsMissingOptionalPhaseLatencyGauges() {
        MonitoredResource resource = resource("Tcp", ResourceType.TCP);

        metricsService.recordCheckResult(CheckResult.builder()
                .resource(resource)
                .status(CheckStatus.AVAILABLE)
                .checkedAt(LocalDateTime.now())
                .latencyMs(50L)
                .build());

        assertThat(gauge("kairos_resource_last_check_latency_seconds", resource, "phase", "total").value())
                .isEqualTo(0.05);
        assertThat(meterRegistry.find("kairos_resource_last_check_latency_seconds")
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .tag("phase", "dns")
                .gauge()).isNull();
    }

    @Test
    void outageMetricsTrackActiveAndResolvedOutages() {
        MonitoredResource resource = resource("Website", ResourceType.HTTP);
        LocalDateTime started = LocalDateTime.now().minusMinutes(5);
        LocalDateTime ended = LocalDateTime.now();
        Outage outage = Outage.builder()
                .resource(resource)
                .startDate(started)
                .endDate(ended)
                .active(true)
                .build();
        when(outageRepository.countByActiveTrue()).thenReturn(1L, 0L);

        metricsService.recordOutageStarted(outage);

        assertThat(gauge("kairos_resource_outage_active", resource).value()).isEqualTo(1.0);
        assertThat(gauge("kairos_resource_active_outage_duration_seconds", resource).value()).isGreaterThan(0.0);
        assertThat(meterRegistry.find("kairos_resource_outage_started")
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("kairos_active_outages").gauge().value()).isEqualTo(1.0);

        metricsService.recordOutageResolved(outage);

        assertThat(gauge("kairos_resource_outage_active", resource).value()).isZero();
        assertThat(gauge("kairos_resource_active_outage_duration_seconds", resource).value()).isZero();
        assertThat(meterRegistry.find("kairos_resource_outage_resolved")
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .counter().count()).isEqualTo(1.0);
        assertThat(timer("kairos_resource_outage_duration", resource).count()).isEqualTo(1L);
        assertThat(meterRegistry.find("kairos_active_outages").gauge().value()).isZero();
    }

    @Test
    void refreshOutageStateInitializesActiveOutageGauges() {
        MonitoredResource resource = resource("Website", ResourceType.HTTP);
        Outage outage = Outage.builder()
                .resource(resource)
                .startDate(LocalDateTime.now().minusMinutes(3))
                .active(true)
                .build();
        when(outageRepository.findAllActiveWithResource()).thenReturn(List.of(outage));

        metricsService.refreshOutageState();

        assertThat(gauge("kairos_resource_outage_active", resource).value()).isEqualTo(1.0);
        assertThat(meterRegistry.find("kairos_active_outages").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void unregisterResourceRemovesResourceMeters() {
        MonitoredResource resource = resource("Website", ResourceType.HTTP);
        metricsService.recordCheckResult(CheckResult.builder()
                .resource(resource)
                .status(CheckStatus.AVAILABLE)
                .checkedAt(LocalDateTime.now())
                .latencyMs(50L)
                .build());

        metricsService.unregisterResource(resource);

        assertThat(resourceGauge(resource)).isNull();
        assertThat(meterRegistry.find("kairos_resource_last_check_latency_seconds")
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .tag("phase", "total")
                .gauge()).isNull();
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

    private Gauge gauge(String name, MonitoredResource resource) {
        return meterRegistry.find(name)
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .gauge();
    }

    private Gauge gauge(String name, MonitoredResource resource, String tagKey, String tagValue) {
        return meterRegistry.find(name)
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .tag(tagKey, tagValue)
                .gauge();
    }

    private Timer timer(String name, MonitoredResource resource, String tagKey, String tagValue) {
        return meterRegistry.find(name)
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .tag(tagKey, tagValue)
                .timer();
    }

    private Timer timer(String name, MonitoredResource resource) {
        return meterRegistry.find(name)
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .timer();
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
