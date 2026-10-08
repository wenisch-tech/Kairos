package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tech.wenisch.kairos.entity.ApiKey;
import tech.wenisch.kairos.entity.ApiKeyPermission;
import tech.wenisch.kairos.repository.ApiKeyRepository;

import java.util.List;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ApplicationTimeService applicationTimeService;

    private ApiKeyService apiKeyService;

    private static final String JWT_SECRET = "test-secret-that-is-long-enough-for-hs256-signing";

    @BeforeEach
    void setUp() {
        lenient().when(applicationTimeService.now()).thenReturn(java.time.LocalDateTime.of(2026, 6, 19, 12, 0));
        apiKeyService = new ApiKeyService(apiKeyRepository, applicationTimeService);
        ReflectionTestUtils.setField(apiKeyService, "jwtSecret", JWT_SECRET);
    }

    // ── create ─────────────────────────────────────────────────────────────

    @Test
    void createReturnsTokenAndPersistedApiKey() {
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.CreatedApiKey result = apiKeyService.create("My Key", "admin@example.com");

        assertThat(result.token()).isNotBlank();
        assertThat(result.apiKey().getName()).isEqualTo("My Key");
        assertThat(result.apiKey().getCreatedBy()).isEqualTo("admin@example.com");
        assertThat(result.apiKey().getKeyId()).isNotBlank();
        assertThat(result.apiKey().getTokenHash()).isNotBlank();
        assertThat(result.apiKey().getPermissions()).containsExactly(ApiKeyPermission.STATUS_READ);
    }

    @Test
    void createStoresExplicitPermissions() {
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.CreatedApiKey result = apiKeyService.create("MCP", "user",
                EnumSet.of(ApiKeyPermission.MCP_ACCESS, ApiKeyPermission.CHECK_EXECUTE));

        assertThat(result.apiKey().getPermissions())
                .containsExactlyInAnyOrder(ApiKeyPermission.MCP_ACCESS, ApiKeyPermission.CHECK_EXECUTE);
    }

    @Test
    void createRejectsEmptyPermissions() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                apiKeyService.create("Empty", "user", EnumSet.noneOf(ApiKeyPermission.class))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void createStoresHashNotPlaintextToken() {
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.CreatedApiKey result = apiKeyService.create("Key", "user");

        assertThat(result.apiKey().getTokenHash()).doesNotContain(result.token());
        assertThat(result.apiKey().getTokenHash()).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    void createSetsCreatedAt() {
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.CreatedApiKey result = apiKeyService.create("Key", "user");

        assertThat(result.apiKey().getCreatedAt()).isNotNull();
    }

    // ── validateToken ──────────────────────────────────────────────────────

    @Test
    void validateTokenReturnsPresentForValidToken() {
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ApiKeyService.CreatedApiKey created = apiKeyService.create("Key", "user");

        ApiKey storedKey = created.apiKey();
        when(apiKeyRepository.findByKeyId(storedKey.getKeyId())).thenReturn(Optional.of(storedKey));

        Optional<ApiKey> result = apiKeyService.validateToken(created.token());

        assertThat(result).isPresent();
    }

    @Test
    void validateTokenReturnsEmptyForTamperedToken() {
        Optional<ApiKey> result = apiKeyService.validateToken("not.a.valid.jwt.token");

        assertThat(result).isEmpty();
    }

    @Test
    void validateTokenReturnsEmptyWhenKeyIdNotInDatabase() {
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ApiKeyService.CreatedApiKey created = apiKeyService.create("Key", "user");

        when(apiKeyRepository.findByKeyId(any())).thenReturn(Optional.empty());

        Optional<ApiKey> result = apiKeyService.validateToken(created.token());

        assertThat(result).isEmpty();
    }

    @Test
    void validateTokenReturnsEmptyWhenHashDoesNotMatch() {
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ApiKeyService.CreatedApiKey created = apiKeyService.create("Key", "user");

        ApiKey storedKey = created.apiKey();
        // Corrupt the stored hash
        storedKey.setTokenHash("0000000000000000000000000000000000000000000000000000000000000000");
        when(apiKeyRepository.findByKeyId(storedKey.getKeyId())).thenReturn(Optional.of(storedKey));

        Optional<ApiKey> result = apiKeyService.validateToken(created.token());

        assertThat(result).isEmpty();
    }

    @Test
    void validateTokenReturnsEmptyForNullInput() {
        Optional<ApiKey> result = apiKeyService.validateToken(null);

        assertThat(result).isEmpty();
    }

    @Test
    void validateTokenReturnsEmptyForBlankInput() {
        Optional<ApiKey> result = apiKeyService.validateToken("   ");

        assertThat(result).isEmpty();
    }

    // ── findAllOrderedByCreatedAtDesc ──────────────────────────────────────

    @Test
    void findAllOrderedDelegatesToRepository() {
        ApiKey key = ApiKey.builder().name("K1").build();
        when(apiKeyRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(key));

        List<ApiKey> result = apiKeyService.findAllOrderedByCreatedAtDesc();

        assertThat(result).containsExactly(key);
    }

    // ── findById ───────────────────────────────────────────────────────────

    @Test
    void findByIdReturnsValueFromRepository() {
        ApiKey key = ApiKey.builder().id(5L).name("K").build();
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        Optional<ApiKey> result = apiKeyService.findById(5L);

        assertThat(result).contains(key);
    }

    @Test
    void updatePermissionsKeepsTheExistingKeyAndTokenIdentity() {
        ApiKey key = ApiKey.builder()
                .id(5L)
                .keyId("stable-key-id")
                .tokenHash("stable-hash")
                .permissions(EnumSet.of(ApiKeyPermission.STATUS_READ))
                .build();
        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(key)).thenReturn(key);

        Optional<ApiKey> result = apiKeyService.updatePermissions(5L,
                EnumSet.of(ApiKeyPermission.MCP_ACCESS, ApiKeyPermission.STATUS_READ));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getKeyId()).isEqualTo("stable-key-id");
        assertThat(result.orElseThrow().getTokenHash()).isEqualTo("stable-hash");
        assertThat(result.orElseThrow().getPermissions())
                .containsExactlyInAnyOrder(ApiKeyPermission.MCP_ACCESS, ApiKeyPermission.STATUS_READ);
    }

    // ── delete ─────────────────────────────────────────────────────────────

    @Test
    void deleteDelegatesToRepository() {
        apiKeyService.delete(3L);

        verify(apiKeyRepository).deleteById(3L);
    }
}
