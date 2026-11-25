package tech.wenisch.kairos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationEvent;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceType;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MattermostNotificationServiceTest {

    @Mock private ApplicationTimeService timeService;

    @SuppressWarnings("unchecked")
    private Map<String, Object> build(NotificationProvider provider, NotificationEvent event) {
        lenient().when(timeService.format(any(), any())).thenReturn("2026-01-01 10:00:00");
        lenient().when(timeService.toEpochSeconds(any())).thenReturn(1767261600L);
        MattermostNotificationService svc =
                new MattermostNotificationService(timeService, new NotificationHttpClientFactory(), new ObjectMapper());
        MonitoredResource resource = new MonitoredResource();
        resource.setName("API");
        resource.setResourceType(ResourceType.HTTP);
        resource.setTarget("https://api.example.com/health");
        Outage outage = new Outage();
        outage.setStartDate(LocalDateTime.of(2026, 1, 1, 10, 0));
        outage.setEndDate(LocalDateTime.of(2026, 1, 1, 11, 5));
        return svc.buildPayload(provider, event, outage, resource);
    }

    @SuppressWarnings("unchecked")
    @Test
    void startedMessageIsRichAndRed() {
        NotificationProvider p = NotificationProvider.builder().mattermostChannel("ops").mattermostUsername("Kairos").build();
        Map<String, Object> payload = build(p, NotificationEvent.OUTAGE_STARTED);

        assertTrue(((String) payload.get("text")).contains("Outage started: API"));
        assertEquals("ops", payload.get("channel"));
        assertEquals("Kairos", payload.get("username"));
        Map<String, Object> att = ((List<Map<String, Object>>) payload.get("attachments")).get(0);
        assertEquals("#FF0000", att.get("color"));
        assertEquals("https://api.example.com/health", att.get("title_link"));
        assertEquals("Kairos", att.get("footer"));
        assertEquals(1767261600L, att.get("ts"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void endedMessageIsGreenWithDuration() {
        Map<String, Object> payload = build(NotificationProvider.builder().build(), NotificationEvent.OUTAGE_ENDED);
        assertFalse(payload.containsKey("channel"));
        Map<String, Object> att = ((List<Map<String, Object>>) payload.get("attachments")).get(0);
        assertEquals("#2ECC71", att.get("color"));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) att.get("fields");
        assertTrue(fields.stream().anyMatch(f -> "Duration".equals(f.get("title")) && "1h 5m".equals(f.get("value"))));
    }
}
