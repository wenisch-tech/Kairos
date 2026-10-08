package tech.wenisch.kairos.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import tech.wenisch.kairos.entity.AppUser;
import tech.wenisch.kairos.repository.AppUserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UiRenderingIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/login", "/announcements", "/outages"})
    void publicPagesRenderWithLocalAssets(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/js/theme.js")))
                .andExpect(content().string(containsString("/css/kairos.css")))
                .andExpect(content().string(not(containsString("/webjars/bootstrap"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"settings", "resources", "resource-types", "resource-discovery",
            "outages", "check-history", "announcements", "announcements/new", "users", "api-keys",
            "proxy-config", "custom-headers", "embed", "about", "notification-providers", "notification-providers/new", "notification-policies", "notification-policies/new"})
    void adminPagesShareNavigationAndRenderFeedback(String page) throws Exception {
        mvc.perform(get("/admin/" + page).with(user("admin@example.com").roles("ADMIN")).flashAttr("successMessage", "Saved successfully")
                        .flashAttr("errorMessage", "Please review your settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("admin-sidebar")))
                .andExpect(content().string(containsString("/js/kairos.js")))
                .andExpect(content().string(not(containsString("data-bs-"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/css/kairos.css", "/js/kairos.js", "/js/theme.js", "/js/editor.js", "/css/editor.css"})
    void builtAssetsArePublic(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void apiKeyPageExplainsAndDefaultsScopedPermissions() throws Exception {
        mvc.perform(get("/admin/api-keys").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Read status")))
                .andExpect(content().string(containsString("Connect through MCP")))
                .andExpect(content().string(containsString("selected: ['STATUS_READ']")))
                .andExpect(content().string(containsString("Select full access")));
    }

    @Test
    void usersPageOffersValidatedPasswordChangesForLocalUsers() throws Exception {
        mvc.perform(get("/admin/users").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Change password")))
                .andExpect(content().string(containsString("/admin/users/update-password/")))
                .andExpect(content().string(containsString("name=\"passwordConfirmation\"")))
                .andExpect(content().string(containsString("minlength=\"8\"")));
    }

    @Test
    void localPasswordUpdateRequiresConfirmationAndReplacesTheStoredHash() throws Exception {
        AppUser localAdmin = userRepository.findByEmail("admin@kairos.local").orElseThrow();
        String originalHash = localAdmin.getPasswordHash();

        mvc.perform(post("/admin/users/update-password/{id}", localAdmin.getId())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("password", "replacement-password")
                        .param("passwordConfirmation", "different-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("errorMessage", "The passwords do not match."));
        assertThat(userRepository.findById(localAdmin.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(originalHash);

        mvc.perform(post("/admin/users/update-password/{id}", localAdmin.getId())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("password", "replacement-password")
                        .param("passwordConfirmation", "replacement-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("successMessage", "Password updated: admin@kairos.local"));

        String updatedHash = userRepository.findById(localAdmin.getId()).orElseThrow().getPasswordHash();
        assertThat(updatedHash).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches("replacement-password", updatedHash)).isTrue();
    }
}
