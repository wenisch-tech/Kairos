package tech.wenisch.kairos.service;

import org.junit.jupiter.api.Test;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationHttpClientFactoryTest {

    private final NotificationHttpClientFactory factory = new NotificationHttpClientFactory();

    @Test
    void ignoresTlsWhenProviderOptionSet() {
        NotificationProvider provider = NotificationProvider.builder().ignoreTls(true).build();
        assertTrue(factory.shouldIgnoreTls(provider, null));
    }

    @Test
    void ignoresTlsWhenResourceSkipsTls() {
        NotificationProvider provider = NotificationProvider.builder().ignoreTls(false).build();
        MonitoredResource resource = new MonitoredResource();
        resource.setSkipTls(true);
        assertTrue(factory.shouldIgnoreTls(provider, resource));
        assertSame(factory.forProvider(provider, resource), factory.forProvider(NotificationProvider.builder().ignoreTls(true).build()));
    }

    @Test
    void verifiesTlsByDefault() {
        NotificationProvider provider = NotificationProvider.builder().ignoreTls(false).build();
        assertFalse(factory.shouldIgnoreTls(provider, new MonitoredResource()));
        assertNotSame(factory.forProvider(provider), factory.forProvider(NotificationProvider.builder().ignoreTls(true).build()));
    }
}
