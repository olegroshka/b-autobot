package com.bbot.core.auth;

import com.bbot.core.exception.BBotAuthException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ClientCredentialsAuth}.
 *
 * <p>Uses a JDK {@link HttpServer} as a mock OAuth2 token endpoint.
 */
class ClientCredentialsAuthTest {

    private static HttpServer server;
    private static String tokenUrl;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);

        // Successful token endpoint
        server.createContext("/oauth2/token", exchange -> {
            String response = """
                    {"access_token":"mock-access-token-123","expires_in":3600,"token_type":"Bearer"}
                    """;
            byte[] body = response.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        // Error token endpoint
        server.createContext("/oauth2/error", exchange -> {
            String response = """
                    {"error":"invalid_client","error_description":"Client ID not found"}
                    """;
            byte[] body = response.getBytes();
            exchange.sendResponseHeaders(401, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        // Short-lived token endpoint (expires in 5 seconds)
        server.createContext("/oauth2/short-lived", exchange -> {
            String response = """
                    {"access_token":"short-lived-token","expires_in":5,"token_type":"Bearer"}
                    """;
            byte[] body = response.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        // Token endpoint that returns no access_token
        server.createContext("/oauth2/no-token", exchange -> {
            String response = """
                    {"token_type":"Bearer","expires_in":3600}
                    """;
            byte[] body = response.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.start();
        tokenUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    // ── Successful token acquisition ─────────────────────────────────────────

    @Test
    void acquiresToken_onConstruction() {
        ClientCredentialsAuth auth = new ClientCredentialsAuth(
                tokenUrl + "/oauth2/token", "my-client", "my-secret", "api://.default");

        assertThat(auth.currentToken()).isEqualTo("mock-access-token-123");
        assertThat(auth.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void apply_addsAuthorizationHeader() {
        ClientCredentialsAuth auth = new ClientCredentialsAuth(
                tokenUrl + "/oauth2/token", "my-client", "my-secret", "api://.default");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("https://api.example.com/test"));
        auth.apply(builder);
        HttpRequest req = builder.GET().build();

        assertThat(req.headers().firstValue("Authorization"))
                .hasValue("Bearer mock-access-token-123");
    }

    // ── Error handling ───────────────────────────────────────────────────────

    @Test
    void tokenRequest_non200_throwsBBotAuthException() {
        assertThatThrownBy(() -> new ClientCredentialsAuth(
                tokenUrl + "/oauth2/error", "bad-client", "bad-secret", ""))
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("invalid_client");
    }

    @Test
    void tokenResponse_missingAccessToken_throws() {
        assertThatThrownBy(() -> new ClientCredentialsAuth(
                tokenUrl + "/oauth2/no-token", "client", "secret", ""))
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("access_token");
    }

    // ── Token expiry detection ───────────────────────────────────────────────

    @Test
    void shortLivedToken_isDetectedAsExpiringSoon() {
        ClientCredentialsAuth auth = new ClientCredentialsAuth(
                tokenUrl + "/oauth2/short-lived", "client", "secret", "");

        // Token expires in 5 seconds — should be detected as expiring soon
        // (within the 60-second refresh margin)
        assertThat(auth.isTokenExpiringSoon()).isTrue();
    }

    @Test
    void longLivedToken_isNotExpiringSoon() {
        ClientCredentialsAuth auth = new ClientCredentialsAuth(
                tokenUrl + "/oauth2/token", "client", "secret", "");

        // Token expires in 3600 seconds — should NOT be expiring soon
        assertThat(auth.isTokenExpiringSoon()).isFalse();
    }

    // ── Scope handling ───────────────────────────────────────────────────────

    @Test
    void emptyScope_doesNotBreakRequest() {
        // Blank scope should still work
        ClientCredentialsAuth auth = new ClientCredentialsAuth(
                tokenUrl + "/oauth2/token", "client", "secret", "");

        assertThat(auth.currentToken()).isEqualTo("mock-access-token-123");
    }
}

