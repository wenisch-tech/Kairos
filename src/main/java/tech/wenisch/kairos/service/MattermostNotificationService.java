package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationEvent;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.entity.Outage;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sends notifications to a Mattermost incoming webhook as message attachments. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MattermostNotificationService {

    private static final String COLOR_RED = "#FF0000";
    private static final String COLOR_GREEN = "#2ECC71";

    private final ApplicationTimeService applicationTimeService;
    private final NotificationHttpClientFactory httpClientFactory;
    private final ObjectMapper objectMapper;

    public void sendOutageNotification(NotificationProvider provider, NotificationEvent event,
                                       Outage outage, MonitoredResource resource) {
        post(provider, resource, buildPayload(provider, event, outage, resource));
    }

    public void sendTestNotification(NotificationProvider provider) {
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("fallback", "Kairos – Test Notification");
        attachment.put("color", COLOR_GREEN);
        attachment.put("author_name", "Kairos");
        attachment.put("title", "Kairos – Test Notification");
        attachment.put("text", "Your Mattermost provider is configured correctly. :tada:");
        attachment.put("footer", "Kairos");
        post(provider, null, envelope(provider, "#### :white_check_mark: Kairos test notification", attachment));
    }

    private void post(NotificationProvider provider, MonitoredResource resource, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
            httpClientFactory.forProvider(provider, resource)
                    .postForEntity(provider.getMattermostWebhookUrl(), request, String.class);
            log.info("Mattermost notification sent via provider '{}'", provider.getName());
        } catch (Exception e) {
            log.error("Failed to send Mattermost notification via provider '{}': {}", provider.getName(), e.getMessage(), e);
            throw new RuntimeException("Mattermost send failed: " + e.getMessage(), e);
        }
    }

    Map<String, Object> buildPayload(NotificationProvider provider, NotificationEvent event,
                                     Outage outage, MonitoredResource resource) {
        boolean started = event == NotificationEvent.OUTAGE_STARTED;
        String name = resource.getName() != null ? resource.getName() : "-";
        String target = resource.getTarget() != null ? resource.getTarget() : "-";
        String headline = (started ? ":warning: Outage started" : ":white_check_mark: Outage resolved") + ": " + name;

        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("Resource", name, true));
        fields.add(field("Type", resource.getResourceType() != null ? resource.getResourceType().name() : "-", true));
        fields.add(field("Target", target, false));
        fields.add(field("Started", outage.getStartDate() != null
                ? applicationTimeService.format(outage.getStartDate(), "yyyy-MM-dd HH:mm:ss") : "-", true));
        if (!started && outage.getEndDate() != null) {
            fields.add(field("Resolved", applicationTimeService.format(outage.getEndDate(), "yyyy-MM-dd HH:mm:ss"), true));
            if (outage.getStartDate() != null) {
                fields.add(field("Duration", formatDuration(Duration.between(outage.getStartDate(), outage.getEndDate())), true));
            }
        }

        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("fallback", headline + " (" + target + ")");
        attachment.put("color", started ? COLOR_RED : COLOR_GREEN);
        attachment.put("author_name", "Kairos");
        attachment.put("title", headline);
        if (target.regionMatches(true, 0, "http://", 0, 7) || target.regionMatches(true, 0, "https://", 0, 8)) {
            attachment.put("title_link", target);
        }
        attachment.put("text", started
                ? "**" + name + "** is not available. Kairos will notify you once it recovers."
                : "**" + name + "** is available again.");
        attachment.put("fields", fields);
        attachment.put("footer", "Kairos");
        LocalDateTime when = started ? outage.getStartDate() : (outage.getEndDate() != null ? outage.getEndDate() : outage.getStartDate());
        if (when != null) {
            attachment.put("ts", applicationTimeService.toEpochSeconds(when));
        }
        return envelope(provider, "#### " + headline + "\n" + target, attachment);
    }

    private static String formatDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private Map<String, Object> envelope(NotificationProvider provider, String text, Map<String, Object> attachment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        if (provider.getMattermostChannel() != null && !provider.getMattermostChannel().isBlank()) {
            body.put("channel", provider.getMattermostChannel().trim());
        }
        if (provider.getMattermostUsername() != null && !provider.getMattermostUsername().isBlank()) {
            body.put("username", provider.getMattermostUsername().trim());
        }
        body.put("attachments", List.of(attachment));
        return body;
    }

    private Map<String, Object> field(String title, String value, boolean isShort) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("short", isShort);
        f.put("title", title);
        f.put("value", value);
        return f;
    }
}
