package dev.mytechprofile.gateway.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

import dev.mytechprofile.gateway.config.GatewayProperties;
import dev.mytechprofile.gateway.config.GatewayProperties.AuthenticationProperties;
import dev.mytechprofile.gateway.config.GatewayProperties.ConnectionProperties;

/**
 * Resolves outbound bearer tokens for OpenAPI connections (client-credentials or static).
 */
@Component
public class OutboundBearerTokenService {

    private final GatewayProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ConcurrentHashMap<String, CachedToken> cache = new ConcurrentHashMap<>();

    public OutboundBearerTokenService(GatewayProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * @return bearer token value without the {@code Bearer } prefix, or {@code null}
     */
    public String resolveBearerToken(String connectionName) {
        ConnectionProperties connection = properties.connections().get(connectionName);
        if (connection == null || connection.authentication() == null) {
            return null;
        }
        AuthenticationProperties auth = connection.authentication();
        if (auth.type() == null || "none".equalsIgnoreCase(auth.type())) {
            return null;
        }
        if ("static-bearer".equalsIgnoreCase(auth.type())) {
            return auth.token();
        }
        if ("oauth2-client-credentials".equalsIgnoreCase(auth.type())) {
            return clientCredentialsToken(connectionName, auth);
        }
        return null;
    }

    private String clientCredentialsToken(String connectionName, AuthenticationProperties auth) {
        CachedToken cached = cache.get(connectionName);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return cached.token();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", auth.clientId());
        form.add("client_secret", auth.clientSecret());

        JsonNode response = restClientBuilder.build()
                .post()
                .uri(auth.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("access_token").isMissingNode()) {
            throw new IllegalStateException("token endpoint returned no access_token for " + connectionName);
        }
        String token = response.path("access_token").asString();
        long expiresIn = response.path("expires_in").asLong(3600);
        cache.put(connectionName, new CachedToken(token, Instant.now().plusSeconds(expiresIn)));
        return token;
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
