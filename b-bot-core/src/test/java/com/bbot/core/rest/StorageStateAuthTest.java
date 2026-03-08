package com.bbot.core.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StorageStateAuth}.
 */
class StorageStateAuthTest {

    // ── Bearer token from clientCredentials mode ─────────────────────────────

    @Test
    void fromStorageState_withBearerToken_addsAuthorizationHeader(@TempDir Path dir)
            throws IOException {
        Path stateFile = dir.resolve("state.json");
        Files.writeString(stateFile, """
                {
                  "cookies": [],
                  "origins": [{
                    "origin": "https://b-bot-auth",
                    "localStorage": [
                      {"name": "b-bot-access-token", "value": "eyJ.test.token"}
                    ]
                  }]
                }
                """);

        StorageStateAuth auth = new StorageStateAuth(stateFile);

        assertThat(auth.hasBearerToken()).isTrue();
        assertThat(auth.cookieCount()).isZero();

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://api.example.com/test"));
        auth.apply(builder);
        HttpRequest req = builder.GET().build();

        assertThat(req.headers().firstValue("Authorization"))
                .hasValue("Bearer eyJ.test.token");
    }

    // ── Cookies from interactive login ───────────────────────────────────────

    @Test
    void fromStorageState_withCookies_addsCookieHeader(@TempDir Path dir)
            throws IOException {
        Path stateFile = dir.resolve("state.json");
        Files.writeString(stateFile, """
                {
                  "cookies": [
                    {"name": "session_id", "value": "abc123", "domain": "example.com"},
                    {"name": "csrf_token", "value": "xyz789", "domain": "example.com"}
                  ],
                  "origins": []
                }
                """);

        StorageStateAuth auth = new StorageStateAuth(stateFile);

        assertThat(auth.hasBearerToken()).isFalse();
        assertThat(auth.cookieCount()).isEqualTo(2);

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://api.example.com/test"));
        auth.apply(builder);
        HttpRequest req = builder.GET().build();

        assertThat(req.headers().firstValue("Cookie"))
                .hasValue("session_id=abc123; csrf_token=xyz789");
    }

    // ── Both token + cookies ─────────────────────────────────────────────────

    @Test
    void fromStorageState_withBothTokenAndCookies(@TempDir Path dir) throws IOException {
        Path stateFile = dir.resolve("state.json");
        Files.writeString(stateFile, """
                {
                  "cookies": [
                    {"name": "sid", "value": "sess123"}
                  ],
                  "origins": [{
                    "origin": "https://b-bot-auth",
                    "localStorage": [
                      {"name": "b-bot-access-token", "value": "tok.123"}
                    ]
                  }]
                }
                """);

        StorageStateAuth auth = new StorageStateAuth(stateFile);

        assertThat(auth.hasBearerToken()).isTrue();
        assertThat(auth.cookieCount()).isEqualTo(1);

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://api.example.com/test"));
        auth.apply(builder);
        HttpRequest req = builder.GET().build();

        assertThat(req.headers().firstValue("Authorization")).hasValue("Bearer tok.123");
        assertThat(req.headers().firstValue("Cookie")).hasValue("sid=sess123");
    }

    // ── Empty storageState ───────────────────────────────────────────────────

    @Test
    void fromStorageState_emptyFile_noHeadersAdded(@TempDir Path dir) throws IOException {
        Path stateFile = dir.resolve("state.json");
        Files.writeString(stateFile, """
                {"cookies": [], "origins": []}
                """);

        StorageStateAuth auth = new StorageStateAuth(stateFile);

        assertThat(auth.hasBearerToken()).isFalse();
        assertThat(auth.cookieCount()).isZero();

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://api.example.com/test"));
        auth.apply(builder);
        HttpRequest req = builder.GET().build();

        assertThat(req.headers().firstValue("Authorization")).isEmpty();
        assertThat(req.headers().firstValue("Cookie")).isEmpty();
    }

    // ── Error cases ──────────────────────────────────────────────────────────

    @SuppressWarnings("ConstantConditions")
    @Test
    void fromStorageState_nullPath_throws() {
        assertThatThrownBy(() -> new StorageStateAuth(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void fromStorageState_nonExistentFile_throws() {
        assertThatThrownBy(() -> new StorageStateAuth(Path.of("nonexistent.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    // ── Factory method on AuthStrategy ───────────────────────────────────────

    @Test
    void authStrategy_fromStorageState_factory(@TempDir Path dir) throws IOException {
        Path stateFile = dir.resolve("state.json");
        Files.writeString(stateFile, """
                {
                  "cookies": [],
                  "origins": [{
                    "origin": "https://b-bot-auth",
                    "localStorage": [
                      {"name": "b-bot-access-token", "value": "factory-token"}
                    ]
                  }]
                }
                """);

        AuthStrategy auth = AuthStrategy.fromStorageState(stateFile);

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://api.example.com/test"));
        auth.apply(builder);
        HttpRequest req = builder.GET().build();

        assertThat(req.headers().firstValue("Authorization"))
                .hasValue("Bearer factory-token");
    }
}

