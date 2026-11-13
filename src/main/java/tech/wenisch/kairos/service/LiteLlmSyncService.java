package tech.wenisch.kairos.service;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.DiscoveryServiceAuth;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceDiscovery;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Discovers the models of a LiteLLM proxy from its {@code /health} endpoint.
 * <p>
 * Every model reported in {@code healthy_endpoints} / {@code unhealthy_endpoints} becomes a
 * {@link ResourceType#LITELLM_MODEL} resource inside the discovery's managed group. The same call
 * records a {@link CheckResult} per model, so the status is tracked (and outages are opened /
 * closed) exactly like for any other resource. Models that disappear from the health report
 * are removed again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiteLlmSyncService {

    static final String SERVICE_TYPE = "LITELLM";
    private static final String USER_AGENT = "Kairos-LiteLLMSync/1.0";
    private static final int MAX_MESSAGE_LENGTH = 1900;
    /** LiteLLM's /health performs a live call per model, so it can take a while. */
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(120);

    private final ResourceService resourceService;
    private final ResourceDiscoveryManagementService resourceDiscoveryManagementService;
    private final MonitoredResourceRepository resourceRepository;
    private final CheckResultRepository checkResultRepository;
    private final OutageService outageService;
    private final MetricsService metricsService;
    private final ResourceStatusStreamService resourceStatusStreamService;
    private final ApplicationTimeService applicationTimeService;
    private final ProxySettingsService proxySettingsService;
    private final TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** One model as reported by the health endpoint. */
    record ModelHealth(String name, String apiBase, boolean healthy, String message) {}

    public void sync(ResourceDiscovery discovery) {
        String baseUrl = normalizeBaseUrl(discovery.getTarget());
        log.debug("Starting LiteLLM discovery sync for {}", baseUrl);

        // Network call happens outside of any DB transaction.
        List<ModelHealth> models;
        try {
            HttpResponse<String> response = fetchHealth(baseUrl, discovery);
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new IllegalStateException("LiteLLM rejected the credentials (HTTP " + response.statusCode()
                        + "). Add a bearer token for this URL under Resource Discovery > LITELLM.");
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("LiteLLM health endpoint returned HTTP " + response.statusCode());
            }
            models = parseHealth(response.body());
        } catch (Exception ex) {
            log.warn("LiteLLM health request to {} failed: {}", baseUrl, ex.getMessage());
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            transactionTemplate.executeWithoutResult(status -> markAllUnavailable(discovery, baseUrl, reason, ex));
            return;
        }

        List<ModelHealth> finalModels = models;
        transactionTemplate.executeWithoutResult(status -> applyHealth(discovery, baseUrl, finalModels));
    }

    private void applyHealth(ResourceDiscovery discovery, String baseUrl, List<ModelHealth> models) {
        ResourceGroup group = resourceDiscoveryManagementService.findOrCreateManagedGroup(discovery);
        Map<String, MonitoredResource> existing = existingByTarget(group);
        Set<String> desired = new HashSet<>();
        int displayOrder = 0;

        for (ModelHealth model : models) {
            String target = targetFor(baseUrl, model);
            String key = target.toLowerCase(Locale.ROOT);
            if (!desired.add(key)) {
                continue; // identical model + api base reported twice
            }

            MonitoredResource resource = existing.get(key);
            if (resource == null) {
                resource = MonitoredResource.builder()
                        .name(model.name())
                        .resourceType(ResourceType.LITELLM_MODEL)
                        .target(target)
                        .skipTls(discovery.isSkipTls())
                        .displayOrder(displayOrder)
                        .active(true)
                        .build();
                resource.getGroups().add(group);
            } else {
                resource.setName(model.name());
                resource.setSkipTls(discovery.isSkipTls());
                resource.setDisplayOrder(displayOrder);
                resource.setActive(true);
            }
            MonitoredResource saved = resourceService.save(resource);
            recordResult(saved, model.healthy() ? CheckStatus.AVAILABLE : CheckStatus.NOT_AVAILABLE,
                    model.message(), model.healthy() ? null : "LITELLM_UNHEALTHY");
            displayOrder++;
        }

        int removed = 0;
        for (Map.Entry<String, MonitoredResource> entry : existing.entrySet()) {
            if (!desired.contains(entry.getKey())) {
                log.debug("Deleting LiteLLM model resource '{}' (no longer reported)", entry.getValue().getName());
                resourceService.delete(entry.getValue().getId());
                removed++;
            }
        }
        log.info("LiteLLM discovery sync finished for '{}' (models: {}, removed: {})",
                discovery.getName(), desired.size(), removed);
    }

    /** The proxy itself could not be reached / queried: every known model is down. */
    private void markAllUnavailable(ResourceDiscovery discovery, String baseUrl, String reason, Exception cause) {
        ResourceGroup group = resourceDiscoveryManagementService.findOrCreateManagedGroup(discovery);
        CheckStatus status = CheckFailureClassifier.resolveStatus(cause);
        for (MonitoredResource resource : existingByTarget(group).values()) {
            recordResult(resource, status, "LiteLLM health check failed: " + reason, "LITELLM_UNREACHABLE");
        }
    }

    private void recordResult(MonitoredResource resource, CheckStatus status, String message, String errorCode) {
        CheckResult saved = checkResultRepository.save(CheckResult.builder()
                .resource(resource)
                .status(status)
                .checkedAt(applicationTimeService.now())
                .message(truncate(message))
                .errorCode(errorCode)
                .build());
        metricsService.recordCheckResult(saved);
        outageService.evaluate(resource);
        try {
            resourceStatusStreamService.publishResourceUpdate(resource);
        } catch (Exception ex) {
            log.trace("Ignoring transient SSE failure for '{}': {}", resource.getName(), ex.getMessage());
        }
    }

    private Map<String, MonitoredResource> existingByTarget(ResourceGroup group) {
        Map<String, MonitoredResource> map = new HashMap<>();
        for (MonitoredResource resource : resourceRepository
                .findByGroups_IdAndResourceType(group.getId(), ResourceType.LITELLM_MODEL)) {
            if (resource.getTarget() != null) {
                map.put(resource.getTarget().trim().toLowerCase(Locale.ROOT), resource);
            }
        }
        return map;
    }

    // ── Parsing ─────────────────────────────────────────────────────────────

    /**
     * Parses a LiteLLM {@code /health} response. Model names that occur more than once (several
     * deployments behind the same model) are disambiguated with their api base.
     */
    List<ModelHealth> parseHealth(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode healthy = root.path("healthy_endpoints");
        JsonNode unhealthy = root.path("unhealthy_endpoints");
        if (!healthy.isArray() && !unhealthy.isArray()) {
            throw new IllegalStateException(
                    "Unexpected response from LiteLLM health endpoint (no healthy_endpoints / unhealthy_endpoints)");
        }

        List<ModelHealth> raw = new ArrayList<>();
        if (healthy.isArray()) {
            for (JsonNode node : healthy) {
                ModelHealth model = toModel(node, true);
                if (model != null) raw.add(model);
            }
        }
        if (unhealthy.isArray()) {
            for (JsonNode node : unhealthy) {
                ModelHealth model = toModel(node, false);
                if (model != null) raw.add(model);
            }
        }

        Map<String, Integer> nameCounts = new HashMap<>();
        raw.forEach(m -> nameCounts.merge(m.name().toLowerCase(Locale.ROOT), 1, Integer::sum));

        List<ModelHealth> result = new ArrayList<>();
        for (ModelHealth m : raw) {
            boolean ambiguous = nameCounts.get(m.name().toLowerCase(Locale.ROOT)) > 1
                    && m.apiBase() != null && !m.apiBase().isBlank();
            result.add(ambiguous
                    ? new ModelHealth(m.name() + " (" + m.apiBase() + ")", m.apiBase(), m.healthy(), m.message())
                    : m);
        }
        result.sort(Comparator.comparing(m -> m.name().toLowerCase(Locale.ROOT)));
        return result;
    }

    private ModelHealth toModel(JsonNode node, boolean healthy) {
        String name = text(node, "model_name");
        if (name == null) name = text(node, "model");
        if (name == null) return null;
        String apiBase = text(node, "api_base");
        String message = healthy ? "Healthy" : firstNonBlank(text(node, "error"), "Reported unhealthy by LiteLLM");
        return new ModelHealth(name, apiBase, healthy, message);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) return message;
        return message.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }

    private static String targetFor(String baseUrl, ModelHealth model) {
        return baseUrl + "#" + model.name();
    }

    static String normalizeBaseUrl(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("LiteLLM URL is required");
        }
        String normalized = target.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.toLowerCase(Locale.ROOT).endsWith("/health")) {
            normalized = normalized.substring(0, normalized.length() - "/health".length());
        }
        return normalized;
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private HttpResponse<String> fetchHealth(String baseUrl, ResourceDiscovery discovery) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .timeout(HEALTH_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET();

        Optional<DiscoveryServiceAuth> auth = resourceDiscoveryManagementService.findMatchingAuth(baseUrl, SERVICE_TYPE);
        auth.map(DiscoveryServiceAuth::getPassword)
                .filter(token -> !token.isBlank())
                .ifPresent(token -> request.header("Authorization", "Bearer " + token.trim()));

        return buildClient(baseUrl, discovery.isSkipTls()).send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpClient buildClient(String target, boolean skipTls) throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);

        proxySettingsService.resolveHttpProxyForTarget(target).ifPresent(endpoint -> {
            builder.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(endpoint.host(), endpoint.port())));
            if (endpoint.hasCredentials()) {
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(endpoint.username(), endpoint.password().toCharArray());
                    }
                });
            }
        });

        if (skipTls) {
            TrustManager[] trustAll = {new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            SSLParameters parameters = new SSLParameters();
            parameters.setEndpointIdentificationAlgorithm(null);
            builder.sslContext(sslContext).sslParameters(parameters);
        }
        return builder.build();
    }
}
