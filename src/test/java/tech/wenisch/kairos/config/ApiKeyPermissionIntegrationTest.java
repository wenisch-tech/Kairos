package tech.wenisch.kairos.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tech.wenisch.kairos.entity.ApiKeyPermission;
import tech.wenisch.kairos.service.ApiKeyService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyPermissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyService apiKeyService;

    @Test
    void protectedHistoryRequiresStatusReadForApiKeys() throws Exception {
        String mcpOnly = token(ApiKeyPermission.MCP_ACCESS);
        String statusReader = token(ApiKeyPermission.STATUS_READ);

        mockMvc.perform(get("/api/resources/999999/history").header(HttpHeaders.AUTHORIZATION, "Bearer " + mcpOnly))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/resources/999999/history").header(HttpHeaders.AUTHORIZATION, "Bearer " + statusReader))
                .andExpect(status().isNotFound());
    }

    @Test
    void resourceWritesRequireResourceManage() throws Exception {
        assertPostPermission("/api/resources", ApiKeyPermission.STATUS_READ, ApiKeyPermission.RESOURCE_MANAGE);
    }

    @Test
    void announcementWritesRequireAnnouncementManage() throws Exception {
        assertPostPermission("/api/announcements", ApiKeyPermission.STATUS_READ,
                ApiKeyPermission.ANNOUNCEMENT_MANAGE);
    }

    @Test
    void publicStatusReadsRemainAvailableToKeysWithoutStatusRead() throws Exception {
        mockMvc.perform(get("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ApiKeyPermission.MCP_ACCESS)))
                .andExpect(status().isOk());
    }

    @Test
    void editingPermissionsChangesTheExistingTokenImmediately() throws Exception {
        ApiKeyService.CreatedApiKey created = apiKeyService.create("editable", "integration-test",
                EnumSet.of(ApiKeyPermission.STATUS_READ));

        mockMvc.perform(get("/api/resources/999999/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + created.token()))
                .andExpect(status().isNotFound());

        apiKeyService.updatePermissions(created.apiKey().getId(), EnumSet.of(ApiKeyPermission.MCP_ACCESS));

        mockMvc.perform(get("/api/resources/999999/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + created.token()))
                .andExpect(status().isForbidden());
    }

    private void assertPostPermission(String path, ApiKeyPermission denied, ApiKeyPermission allowed) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(denied)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(allowed)))
                .andExpect(status().isBadRequest());
    }

    private String token(ApiKeyPermission permission) {
        return apiKeyService.create("integration-" + permission, "integration-test", EnumSet.of(permission)).token();
    }
}
