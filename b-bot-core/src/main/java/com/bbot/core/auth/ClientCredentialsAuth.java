package com.bbot.core.auth;

import com.bbot.core.exception.BBotAuthException;
import com.bbot.core.rest.AuthStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * OAuth2 {@code client_credentials} grant {@link AuthStrategy} implementation.
 *
 * <p>Acquires an access token from the configured OAuth2 token endpoint and
 * injects it as an {@code Authorization: Bearer} header on every outbound
 * HTTP request. The token is automatically refreshed when it is within
 * 60 seconds of expiry.
 *
 * <h2>Thread safety</h2>
 * This class is thread-safe. The {@link #refreshToken()} method is synchronized
 * to prevent concurrent token requests. The volatile fields ensure visibility
 * across threads.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SsoAuthConfig cfg = SsoAuthConfig.from(bbotConfig);
 * AuthStrategy auth = new ClientCredentialsAuth(cfg);
 * RestProbe probe = RestProbe.builder()
 *     .apiBase(apiBase)
 *     .auth(auth)
 *     .build();
 * }</pre>
 *
 * @see SsoAuthConfig
 * @see SsoAuthManager
 */
public final class ClientCredentialsAuth implements AuthStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ClientCredentialsAuth.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;

    private volatile String  accessToken;
    private volatile Instant expiresAt;

    /**
     * Creates a new {@code ClientCredentialsAuth} and immediately acquires
     * an access token.
     *
     * @param config the SSO auth configuration (must have mode=CLIENT_CREDENTIALS)
     * @throws BBotAuthException if the initial token request fails
     */
    @SuppressWarnings("unused")
    public ClientCredentialsAuth(SsoAuthConfig config) {
        this.tokenUrl     = config.tokenUrl();
        this.clientId     = config.clientId();
        this.clientSecret = config.clientSecret();
        this.scope        = config.scope();
        refreshToken();
    }

    /**
     * Package-private constructor for testing — allows injecting token endpoint details
     * without a full {@link SsoAuthConfig}.
     */
    ClientCredentialsAuth(String tokenUrl, String clientId, String clientSecret, String scope) {
        this.tokenUrl     = tokenUrl;
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
        this.scope        = scope;
        refreshToken();
    }

    @Override
    public void apply(HttpRequest.Builder builder) {
        if (isTokenExpiringSoon()) {
            LOG.debug("Access token expiring soon — refreshing proactively");
            refreshToken();
        }
        builder.header("Authorization", "Bearer " + accessToken);
    }

    /**
     * Returns the current access token (for testing/debugging).
     */
    String currentToken() {
        return accessToken;
    }

    /**
     * Returns the token expiry instant (for testing/debugging).
     */
    Instant expiresAt() {
        return expiresAt;
    }

    /**
     * Returns {@code true} if the token will expire within the refresh margin.
     */
    boolean isTokenExpiringSoon() {
        return expiresAt != null && Instant.now().isAfter(expiresAt.minus(REFRESH_MARGIN));
    }

    // ── Token acquisition ────────────────────────────────────────────────────

    synchronized void refreshToken() {
        LOG.info("OAuth2 client_credentials — requesting token from: {}", tokenUrl);

        String formBody = "grant_type=client_credentials"
                + "&client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + (scope.isBlank() ? "" : "&scope=" + urlEncode(scope));

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new BBotAuthException(
                        "OAuth client_credentials token request failed: HTTP " + resp.statusCode()
                        + "\nResponse: " + resp.body()
                        + "\nCheck b-bot.auth.tokenUrl, clientId, clientSecret, and scope.");
            }

            parseTokenResponse(resp.body());
            LOG.info("OAuth token acquired — expires at: {}", expiresAt);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new BBotAuthException(
                    "OAuth client_credentials token request failed: " + e.getMessage(), e);
        }
    }

    private void parseTokenResponse(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);

            JsonNode tokenNode = root.get("access_token");
            if (tokenNode == null || tokenNode.isNull() || tokenNode.asText().isBlank()) {
                throw new BBotAuthException(
                        "OAuth response missing 'access_token' field.\nResponse: " + responseBody);
            }
            this.accessToken = tokenNode.asText();

            // Parse expires_in (seconds) — default to 1 hour if not present
            int expiresIn = root.has("expires_in") ? root.get("expires_in").asInt(3600) : 3600;
            this.expiresAt = Instant.now().plusSeconds(expiresIn);

        } catch (BBotAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new BBotAuthException(
                    "Failed to parse OAuth token response: " + e.getMessage(), e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

