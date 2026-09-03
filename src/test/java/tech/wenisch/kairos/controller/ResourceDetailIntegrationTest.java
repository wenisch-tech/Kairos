package tech.wenisch.kairos.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.OutageRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceDetailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MonitoredResourceRepository resourceRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Autowired
    private OutageRepository outageRepository;

    @Test
    void detailPageRendersForAResourceWhoseLatestCheckReturned503() throws Exception {
        MonitoredResource resource = resourceRepository.save(MonitoredResource.builder()
                .name("Unavailable service")
                .resourceType(ResourceType.HTTP)
                .target("https://example.invalid")
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build());
        checkResultRepository.save(CheckResult.builder()
                .resource(resource)
                .status(CheckStatus.NOT_AVAILABLE)
                .errorCode("503")
                .message("HTTP 503 Service Unavailable")
                .checkedAt(LocalDateTime.now())
                .build());
        outageRepository.save(Outage.builder()
                .resource(resource)
                .startDate(LocalDateTime.now().minusMinutes(5))
                .active(true)
                .build());

        mockMvc.perform(get("/resources/{id}", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("detail"));
    }
}
