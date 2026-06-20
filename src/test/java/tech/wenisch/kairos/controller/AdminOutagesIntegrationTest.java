package tech.wenisch.kairos.controller;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.repository.OutageRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOutagesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutageRepository outageRepository;

    @Test
    void adminOutagesPageLoads() throws Exception {
                outageRepository.deleteAll();

        mockMvc.perform(get("/admin/outages")
                        .param("status", "all")
                        .param("q", "")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void singleOutageDeleteRemovesRecord() throws Exception {
                outageRepository.deleteAll();

        Outage outage = outageRepository.save(Outage.builder()
                .startDate(LocalDateTime.now().minusHours(1))
                .active(true)
                .build());

        mockMvc.perform(post("/admin/outages/delete/{id}", outage.getId())
                        .param("status", "all")
                        .param("q", "")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/outages?status=all&q="));

        assertThat(outageRepository.findById(outage.getId())).isEmpty();
    }

    @Test
    void deleteSelectedRemovesProvidedIds() throws Exception {
                outageRepository.deleteAll();

        Outage first = outageRepository.save(Outage.builder()
                .startDate(LocalDateTime.now().minusHours(3))
                .active(false)
                .endDate(LocalDateTime.now().minusHours(2))
                .build());
        Outage second = outageRepository.save(Outage.builder()
                .startDate(LocalDateTime.now().minusHours(2))
                .active(false)
                .endDate(LocalDateTime.now().minusHours(1))
                .build());
        Outage survivor = outageRepository.save(Outage.builder()
                .startDate(LocalDateTime.now().minusHours(1))
                .active(true)
                .build());

        mockMvc.perform(post("/admin/outages/delete-selected")
                        .param("outageIds", String.valueOf(first.getId()), String.valueOf(second.getId()))
                        .param("status", "all")
                        .param("q", "")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/outages?status=all&q="));

        List<Outage> remaining = outageRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(survivor.getId());
    }

    @Test
    void deleteResolvedRemovesOnlyResolvedOutages() throws Exception {
                outageRepository.deleteAll();

        outageRepository.save(Outage.builder()
                .startDate(LocalDateTime.now().minusHours(4))
                .active(false)
                .endDate(LocalDateTime.now().minusHours(3))
                .build());
        outageRepository.save(Outage.builder()
                .startDate(LocalDateTime.now().minusHours(3))
                .active(false)
                .endDate(LocalDateTime.now().minusHours(2))
                .build());
        Outage active = outageRepository.save(Outage.builder()
                .startDate(LocalDateTime.now().minusHours(1))
                .active(true)
                .build());

        mockMvc.perform(post("/admin/outages/delete-resolved")
                        .param("status", "all")
                        .param("q", "")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/outages?status=all&q="));

        List<Outage> remaining = outageRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(active.getId());
        assertThat(remaining.get(0).isActive()).isTrue();
    }
}
