package tech.wenisch.kairos.service;

import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

@Service
@RequiredArgsConstructor
public class AvailabilityFormattingService {

    public static final int DEFAULT_DECIMAL_PLACES = 2;
    public static final int MIN_DECIMAL_PLACES = 2;
    public static final int MAX_DECIMAL_PLACES = 5;

    private final ResourceTypeConfigRepository resourceTypeConfigRepository;

    public int getConfiguredDecimalPlaces() {
        return resourceTypeConfigRepository.findAll().stream()
                .map(ResourceTypeConfig::getAvailabilityPercentageDecimalPlaces)
                .findFirst()
                .map(this::sanitizeDecimalPlaces)
                .orElse(DEFAULT_DECIMAL_PLACES);
    }

    public int sanitizeDecimalPlaces(Integer value) {
        if (value == null) {
            return DEFAULT_DECIMAL_PLACES;
        }
        return Math.max(MIN_DECIMAL_PLACES, Math.min(MAX_DECIMAL_PLACES, value));
    }

    public String formatPercentage(double value, int decimalPlaces) {
        Locale locale = LocaleContextHolder.getLocale();
        NumberFormat format = NumberFormat.getNumberInstance(locale != null ? locale : Locale.getDefault());
        int sanitizedDecimalPlaces = sanitizeDecimalPlaces(decimalPlaces);
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(sanitizedDecimalPlaces);
        format.setMaximumFractionDigits(sanitizedDecimalPlaces);
        return format.format(value) + "%";
    }
}
