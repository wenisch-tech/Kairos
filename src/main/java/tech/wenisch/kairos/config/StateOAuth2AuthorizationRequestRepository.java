package tech.wenisch.kairos.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class StateOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Duration REQUEST_TTL = Duration.ofMinutes(5);
    private static final Map<String, StoredAuthorizationRequest> AUTHORIZATION_REQUESTS = new ConcurrentHashMap<>();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        cleanupExpiredRequests();

        String state = request.getParameter("state");
        if (state == null || state.isBlank()) {
            return null;
        }

        StoredAuthorizationRequest stored = AUTHORIZATION_REQUESTS.get(state);
        if (stored == null || stored.isExpired()) {
            AUTHORIZATION_REQUESTS.remove(state);
            return null;
        }

        return stored.authorizationRequest();
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        cleanupExpiredRequests();

        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }

        AUTHORIZATION_REQUESTS.put(authorizationRequest.getState(),
                new StoredAuthorizationRequest(authorizationRequest, Instant.now().plus(REQUEST_TTL)));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                HttpServletResponse response) {
        String state = request.getParameter("state");
        if (state == null || state.isBlank()) {
            return null;
        }

        StoredAuthorizationRequest stored = AUTHORIZATION_REQUESTS.remove(state);
        if (stored == null || stored.isExpired()) {
            return null;
        }

        return stored.authorizationRequest();
    }

    private void cleanupExpiredRequests() {
        AUTHORIZATION_REQUESTS.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private record StoredAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, Instant expiresAt) {
        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
