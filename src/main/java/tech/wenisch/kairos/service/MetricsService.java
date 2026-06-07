package tech.wenisch.kairos.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.OutageRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final MonitoredResourceRepository resourceRepository;
    private final CheckResultRepository checkResultRepository;
    private final OutageRepository outageRepository;

    private final Map<Long, ResourceMetricState> resourceStates = new ConcurrentHashMap<>();
    private final AtomicLong activeOutages = new AtomicLong(0);
    private final AtomicBoolean globalMetricsRegistered = new AtomicBoolean(false);

    @PostConstruct
    public void registerMetrics() {
        ensureGlobalMetrics();

        List<MonitoredResource> resources = resourceRepository.findByActiveTrue();
        for (MonitoredResource resource : resources) {
            registerOrUpdateResource(resource);
            checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource)
                    .ifPresent(this::updateLatestCheckGauges);
        }

        refreshOutageState();
    }

    public void registerResourceMetric(MonitoredResource resource) {
        registerOrUpdateResource(resource);
    }

    public void registerOrUpdateResource(MonitoredResource resource) {
        if (resource != null && resource.getId() != null && !resource.isActive()) {
            unregisterResource(resource);
            return;
        }
        if (!isMetricResource(resource)) {
            return;
        }

        Long resourceId = resource.getId();
        ResourceType resourceType = resource.getResourceType();
        String resourceName = normalizeResourceName(resource.getName());
        ResourceMetricState existing = resourceStates.get(resourceId);

        if (existing != null && existing.matches(resourceName, resourceType)) {
            return;
        }

        if (existing != null) {
            removeState(existing);
        }

        ResourceMetricState state = new ResourceMetricState(resourceId, resourceName, resourceType);
        resourceStates.put(resourceId, state);

        registerGauge("kairos_resource_status", state, current -> current.status.get(),
                "Resource status: 1=available, 0=not_available, -1=unknown");
        registerGauge("kairos_resource_last_check_timestamp_seconds", state, current -> current.lastCheckTimestampSeconds.get(),
                "Unix timestamp of the latest persisted Kairos check");
        registerGauge("kairos_resource_outage_active", state, current -> current.outageActive.get(),
                "Whether the resource currently has an active outage: 1=active, 0=inactive");
        registerGauge("kairos_resource_active_outage_duration_seconds", state, ResourceMetricState::activeOutageDurationSeconds,
                "Current active outage duration in seconds, or 0 when no outage is active");

        state.ensureLatestLatencyGauge(meterRegistry, "total");
    }

    public void unregisterResource(MonitoredResource resource) {
        if (resource == null || resource.getId() == null) {
            return;
        }
        ResourceMetricState removed = resourceStates.remove(resource.getId());
        if (removed != null) {
            removeState(removed);
        }
    }

    public void recordCheckResult(CheckResult result) {
        if (result == null || !isMetricResource(result.getResource())) {
            return;
        }

        registerOrUpdateResource(result.getResource());
        ResourceMetricState state = resourceStates.get(result.getResource().getId());
        if (state == null) {
            return;
        }

        updateLatestCheckGauges(result);

        String status = normalizeStatus(result.getStatus());
        String errorCode = normalizeErrorCode(result.getErrorCode());
        counter("kairos_resource_checks", state, Tags.of("status", status, "error_code", errorCode))
                .increment();

        if (result.getLatencyMs() != null && result.getLatencyMs() >= 0) {
            timer("kairos_resource_check_duration", state, Tags.of("status", status))
                    .record(result.getLatencyMs(), TimeUnit.MILLISECONDS);
        }

        recordPhaseLatency(state, "dns", result.getDnsResolutionMs());
        recordPhaseLatency(state, "connect", result.getConnectMs());
        recordPhaseLatency(state, "tls", result.getTlsHandshakeMs());
    }

    public void recordOutageStarted(Outage outage) {
        ensureGlobalMetrics();
        if (outage == null || !isMetricResource(outage.getResource())) {
            return;
        }
        registerOrUpdateResource(outage.getResource());
        ResourceMetricState state = resourceStates.get(outage.getResource().getId());
        if (state == null) {
            return;
        }

        counter("kairos_resource_outage_started", state, Tags.empty()).increment();
        state.markOutageActive(outage.getStartDate());
        activeOutages.set(outageRepository.countByActiveTrue());
    }

    public void recordOutageResolved(Outage outage) {
        ensureGlobalMetrics();
        if (outage == null || !isMetricResource(outage.getResource())) {
            return;
        }
        registerOrUpdateResource(outage.getResource());
        ResourceMetricState state = resourceStates.get(outage.getResource().getId());
        if (state == null) {
            return;
        }

        counter("kairos_resource_outage_resolved", state, Tags.empty()).increment();
        if (outage.getStartDate() != null && outage.getEndDate() != null) {
            long seconds = Math.max(0L, Duration.between(outage.getStartDate(), outage.getEndDate()).getSeconds());
            timer("kairos_resource_outage_duration", state, Tags.empty()).record(seconds, TimeUnit.SECONDS);
        }
        state.clearOutage();
        activeOutages.set(outageRepository.countByActiveTrue());
    }

    public void refreshOutageState() {
        ensureGlobalMetrics();
        resourceStates.values().forEach(ResourceMetricState::clearOutage);
        List<Outage> outages = outageRepository.findAllActiveWithResource();
        for (Outage outage : outages) {
            if (!isMetricResource(outage.getResource())) {
                continue;
            }
            registerOrUpdateResource(outage.getResource());
            ResourceMetricState state = resourceStates.get(outage.getResource().getId());
            if (state != null) {
                state.markOutageActive(outage.getStartDate());
            }
        }
        activeOutages.set(outages.size());
    }

    private void ensureGlobalMetrics() {
        if (!globalMetricsRegistered.compareAndSet(false, true)) {
            return;
        }
        Gauge.builder("kairos_active_outages", activeOutages, AtomicLong::get)
                .description("Number of currently active Kairos outages")
                .strongReference(true)
                .register(meterRegistry);
    }

    private void updateLatestCheckGauges(CheckResult result) {
        if (result == null || !isMetricResource(result.getResource())) {
            return;
        }
        ResourceMetricState state = resourceStates.get(result.getResource().getId());
        if (state == null) {
            return;
        }

        state.status.set(statusValue(result.getStatus()));
        state.lastCheckTimestampSeconds.set(toEpochSeconds(result.getCheckedAt()));
        state.updateLatestLatency("total", result.getLatencyMs(), meterRegistry);
        state.updateLatestLatency("dns", result.getDnsResolutionMs(), meterRegistry);
        state.updateLatestLatency("connect", result.getConnectMs(), meterRegistry);
        state.updateLatestLatency("tls", result.getTlsHandshakeMs(), meterRegistry);
    }

    private void recordPhaseLatency(ResourceMetricState state, String phase, Long latencyMs) {
        if (latencyMs == null || latencyMs < 0) {
            return;
        }
        timer("kairos_resource_check_phase_duration", state, Tags.of("phase", phase))
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    private void registerGauge(String name, ResourceMetricState state, java.util.function.ToDoubleFunction<ResourceMetricState> valueFunction,
                               String description) {
        Meter meter = Gauge.builder(name, state, valueFunction)
                .tags(state.tags())
                .description(description)
                .strongReference(true)
                .register(meterRegistry);
        state.meterIds.add(meter.getId());
    }

    private Counter counter(String name, ResourceMetricState state, Tags extraTags) {
        Counter counter = Counter.builder(name)
                .tags(state.tags())
                .tags(extraTags)
                .register(meterRegistry);
        state.meterIds.add(counter.getId());
        return counter;
    }

    private Timer timer(String name, ResourceMetricState state, Tags extraTags) {
        Timer timer = Timer.builder(name)
                .tags(state.tags())
                .tags(extraTags)
                .publishPercentileHistogram()
                .register(meterRegistry);
        state.meterIds.add(timer.getId());
        return timer;
    }

    private void removeState(ResourceMetricState state) {
        List<Meter.Id> meterIds = new ArrayList<>(state.meterIds);
        for (Meter.Id meterId : meterIds) {
            meterRegistry.remove(meterId);
        }
        state.meterIds.clear();
    }

    private boolean isMetricResource(MonitoredResource resource) {
        return resource != null
                && resource.getId() != null
                && resource.getResourceType() != null
                && resource.getName() != null;
    }

    private int statusValue(CheckStatus status) {
        if (status == null) {
            return -1;
        }
        return switch (status) {
            case AVAILABLE -> 1;
            case NOT_AVAILABLE -> 0;
            case UNKNOWN -> -1;
        };
    }

    private String normalizeStatus(CheckStatus status) {
        return status == null ? "UNKNOWN" : status.name();
    }

    private String normalizeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "none";
        }
        return errorCode.trim();
    }

    private String normalizeResourceName(String name) {
        return name == null || name.isBlank() ? "unnamed" : name.trim();
    }

    private long toEpochSeconds(LocalDateTime timestamp) {
        if (timestamp == null) {
            return 0L;
        }
        return timestamp.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private final class ResourceMetricState {
        private final Long resourceId;
        private final String resourceName;
        private final ResourceType resourceType;
        private final AtomicInteger status = new AtomicInteger(-1);
        private final AtomicLong lastCheckTimestampSeconds = new AtomicLong(0);
        private final Map<String, AtomicLong> latestLatencyMillis = new ConcurrentHashMap<>();
        private final AtomicInteger outageActive = new AtomicInteger(0);
        private final AtomicLong activeOutageStartSeconds = new AtomicLong(0);
        private final Set<Meter.Id> meterIds = new CopyOnWriteArraySet<>();

        private ResourceMetricState(Long resourceId, String resourceName, ResourceType resourceType) {
            this.resourceId = resourceId;
            this.resourceName = resourceName;
            this.resourceType = resourceType;
        }

        private Tags tags() {
            return Tags.of("resource_name", resourceName, "resource_type", resourceType.name());
        }

        private boolean matches(String name, ResourceType type) {
            return Objects.equals(resourceName, name) && resourceType == type;
        }

        private void updateLatestLatency(String phase, Long latencyMs, MeterRegistry registry) {
            if (latencyMs == null || latencyMs < 0) {
                return;
            }
            ensureLatestLatencyGauge(registry, phase).set(latencyMs);
        }

        private AtomicLong ensureLatestLatencyGauge(MeterRegistry registry, String phase) {
            return latestLatencyMillis.computeIfAbsent(phase, key -> {
                AtomicLong value = new AtomicLong(0);
                Meter meter = Gauge.builder("kairos_resource_last_check_latency_seconds", value,
                                latency -> latency.get() / 1000.0)
                        .tags(tags())
                        .tag("phase", key)
                        .description("Latest persisted Kairos check latency in seconds")
                        .strongReference(true)
                        .register(registry);
                meterIds.add(meter.getId());
                return value;
            });
        }

        private void markOutageActive(LocalDateTime startDate) {
            outageActive.set(1);
            activeOutageStartSeconds.set(toEpochSeconds(startDate));
        }

        private void clearOutage() {
            outageActive.set(0);
            activeOutageStartSeconds.set(0);
        }

        private double activeOutageDurationSeconds() {
            if (outageActive.get() == 0 || activeOutageStartSeconds.get() <= 0) {
                return 0.0;
            }
            return Math.max(0L, java.time.Instant.now().getEpochSecond() - activeOutageStartSeconds.get());
        }
    }
}
