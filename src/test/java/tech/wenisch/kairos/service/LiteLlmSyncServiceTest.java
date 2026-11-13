package tech.wenisch.kairos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LiteLlmSyncServiceTest {

    private final LiteLlmSyncService service =
            new LiteLlmSyncService(null, null, null, null, null, null, null, null, null, null);

    @Test
    void parsesHealthyAndUnhealthyModels() throws Exception {
        String body = """
                {"healthy_endpoints":[{"model":"gpt-4o","api_base":"https://a"}],
                 "unhealthy_endpoints":[{"model":"claude","error":"401 invalid key"}],
                 "healthy_count":1,"unhealthy_count":1}
                """;
        List<LiteLlmSyncService.ModelHealth> models = service.parseHealth(body);
        assertEquals(2, models.size());
        assertEquals("claude", models.get(0).name());
        assertFalse(models.get(0).healthy());
        assertEquals("401 invalid key", models.get(0).message());
        assertEquals("gpt-4o", models.get(1).name());
        assertTrue(models.get(1).healthy());
    }

    @Test
    void disambiguatesDuplicateModelNamesByApiBase() throws Exception {
        String body = """
                {"healthy_endpoints":[{"model":"gpt-4o","api_base":"https://a"},{"model":"gpt-4o","api_base":"https://b"}],
                 "unhealthy_endpoints":[]}
                """;
        List<LiteLlmSyncService.ModelHealth> models = service.parseHealth(body);
        assertEquals("gpt-4o (https://a)", models.get(0).name());
        assertEquals("gpt-4o (https://b)", models.get(1).name());
    }

    @Test
    void rejectsUnexpectedPayload() {
        assertThrows(IllegalStateException.class, () -> service.parseHealth("{\"error\":\"nope\"}"));
    }

    @Test
    void normalizesBaseUrl() {
        assertEquals("https://llm.example.com", LiteLlmSyncService.normalizeBaseUrl("llm.example.com/"));
        assertEquals("http://llm:4000", LiteLlmSyncService.normalizeBaseUrl("http://llm:4000/health"));
        assertThrows(IllegalArgumentException.class, () -> LiteLlmSyncService.normalizeBaseUrl(" "));
    }
}
