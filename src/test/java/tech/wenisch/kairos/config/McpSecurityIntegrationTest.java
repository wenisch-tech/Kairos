package tech.wenisch.kairos.config;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tech.wenisch.kairos.service.ApiKeyService;
import tech.wenisch.kairos.entity.ApiKeyPermission;

import java.util.EnumSet;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class McpSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyService apiKeyService;

    private String validToken;

    @BeforeEach
    void setUp() {
        validToken = apiKeyService.create("mcp-test", "integration-test",
                EnumSet.of(ApiKeyPermission.MCP_ACCESS)).token();
    }

    @Test
    void sseWithoutApiKeyRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/sse").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(unauthenticated());
    }

    @Test
    void sseWithApiKeyDoesNotRedirectToLogin() throws Exception {
        mockMvc.perform(get("/sse")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void mcpMessageWithInvalidApiKeyReturnsUnauthorizedInsteadOfLoginRedirect() throws Exception {
        mockMvc.perform(post("/mcp/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Test
    void sseWithValidKeyWithoutMcpAccessIsForbidden() throws Exception {
        String statusOnlyToken = apiKeyService.create("status-only", "integration-test",
                EnumSet.of(ApiKeyPermission.STATUS_READ)).token();

        mockMvc.perform(get("/sse")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + statusOnlyToken))
                .andExpect(status().isForbidden());
    }
}
