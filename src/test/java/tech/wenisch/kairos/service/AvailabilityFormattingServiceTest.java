package tech.wenisch.kairos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

@ExtendWith(MockitoExtension.class)
class AvailabilityFormattingServiceTest {

    @Mock
    private ResourceTypeConfigRepository resourceTypeConfigRepository;

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void getConfiguredDecimalPlacesDefaultsToTwoWhenNoConfigExists() {
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of());
        AvailabilityFormattingService service = new AvailabilityFormattingService(resourceTypeConfigRepository);

        assertThat(service.getConfiguredDecimalPlaces()).isEqualTo(2);
    }

    @Test
    void getConfiguredDecimalPlacesClampsConfiguredValueToSupportedRange() {
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(
                ResourceTypeConfig.builder()
                        .availabilityPercentageDecimalPlaces(9)
                        .build()));
        AvailabilityFormattingService service = new AvailabilityFormattingService(resourceTypeConfigRepository);

        assertThat(service.getConfiguredDecimalPlaces()).isEqualTo(5);
        assertThat(service.sanitizeDecimalPlaces(1)).isEqualTo(2);
    }

    @Test
    void formatPercentageUsesLocaleAndConfiguredScale() {
        AvailabilityFormattingService service = new AvailabilityFormattingService(resourceTypeConfigRepository);
        LocaleContextHolder.setLocale(Locale.GERMANY);

        assertThat(service.formatPercentage(99.98765, 5)).isEqualTo("99,98765%");
        assertThat(service.formatPercentage(99.9, 2)).isEqualTo("99,90%");
    }
}
