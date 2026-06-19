package tech.wenisch.kairos.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneRulesException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

@Service("kairosTime")
@RequiredArgsConstructor
public class ApplicationTimeService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MINUTE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HTML_INPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final ZoneId STORAGE_ZONE = ZoneOffset.UTC;

    private final ResourceTypeConfigRepository resourceTypeConfigRepository;

    public LocalDateTime now() {
        return LocalDateTime.now(STORAGE_ZONE);
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZoneId());
    }

    public String timeZoneId() {
        return resourceTypeConfigRepository.findAll().stream()
                .map(ResourceTypeConfig::getTimeZone)
                .filter(value -> value != null && !value.isBlank())
                .filter(this::isValidZoneId)
                .findFirst()
                .orElseGet(() -> ZoneId.systemDefault().getId());
    }

    public String timeZoneLabel() {
        return labelFor(timeZoneId());
    }

    public long toEpochSeconds(LocalDateTime timestamp) {
        if (timestamp == null) {
            return 0L;
        }
        return timestamp.atZone(STORAGE_ZONE).toEpochSecond();
    }

    public long nowEpochSeconds() {
        return now().atZone(STORAGE_ZONE).toEpochSecond();
    }

    public String format(LocalDateTime timestamp) {
        return format(timestamp, "yyyy-MM-dd HH:mm:ss");
    }

    public String format(LocalDateTime timestamp, String pattern) {
        if (timestamp == null) {
            return "";
        }
        return toDisplayTime(timestamp).format(DateTimeFormatter.ofPattern(pattern));
    }

    public String formatMinute(LocalDateTime timestamp) {
        return timestamp == null ? "" : toDisplayTime(timestamp).format(MINUTE_FORMATTER);
    }

    public String formatDate(LocalDateTime timestamp) {
        return timestamp == null ? "" : toDisplayTime(timestamp).format(DATE_FORMATTER);
    }

    public String formatDateTime(LocalDateTime timestamp) {
        return timestamp == null ? "" : toDisplayTime(timestamp).format(DATE_TIME_FORMATTER);
    }

    public String formatHtmlInput(LocalDateTime timestamp) {
        return timestamp == null ? "" : toDisplayTime(timestamp).format(HTML_INPUT_FORMATTER);
    }

    public String formatIsoUtc(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public LocalDateTime parseDisplayDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        LocalDateTime displayTime = LocalDateTime.parse(value);
        return displayTime.atZone(zoneId())
                .withZoneSameInstant(STORAGE_ZONE)
                .toLocalDateTime();
    }

    public LocalDateTime toDisplayTime(LocalDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }
        ZonedDateTime stored = timestamp.atZone(STORAGE_ZONE);
        return stored.withZoneSameInstant(zoneId()).toLocalDateTime();
    }

    @Transactional
    public void saveTimeZone(String rawTimeZone) {
        String normalized = normalizeTimeZone(rawTimeZone);
        for (ResourceTypeConfig config : resourceTypeConfigRepository.findAll()) {
            config.setTimeZone(normalized);
            resourceTypeConfigRepository.save(config);
        }
    }

    public List<TimeZoneOption> timeZoneOptions() {
        Instant now = Instant.now();
        return ZoneId.getAvailableZoneIds().stream()
                .sorted(Comparator
                        .comparing((String id) -> ZoneId.of(id).getRules().getOffset(now))
                        .thenComparing(String::toString))
                .map(id -> new TimeZoneOption(id, labelFor(id, now)))
                .toList();
    }

    public String normalizeTimeZone(String rawTimeZone) {
        if (rawTimeZone == null || rawTimeZone.isBlank()) {
            return ZoneId.systemDefault().getId();
        }
        String trimmed = rawTimeZone.trim();
        try {
            return ZoneId.of(trimmed).getId();
        } catch (ZoneRulesException ex) {
            return ZoneId.systemDefault().getId();
        }
    }

    private boolean isValidZoneId(String value) {
        try {
            ZoneId.of(value);
            return true;
        } catch (ZoneRulesException ex) {
            return false;
        }
    }

    private String labelFor(String zoneId) {
        return labelFor(zoneId, Instant.now());
    }

    private String labelFor(String zoneId, Instant now) {
        ZoneId zone = ZoneId.of(zoneId);
        ZoneOffset offset = zone.getRules().getOffset(now);
        String city = zoneId.contains("/") ? zoneId.substring(zoneId.lastIndexOf('/') + 1).replace('_', ' ') : zoneId;
        return "GMT" + offset.getId().replace("Z", "+00:00") + " - " + city + " (" + zoneId + ")";
    }

    public record TimeZoneOption(String id, String label) {
    }
}
