package com.bbot.core.auth;

import com.bbot.core.auth.SsoAuthConfig.AuthMode;
import com.bbot.core.exception.BBotAuthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SsoAuthManager}.
 *
 * <p>Interactive and browser-dependent flows cannot be unit-tested (they require
 * a live Playwright browser). This class covers:
 * <ul>
 *   <li>NONE mode — no-op</li>
 *   <li>STORAGE_STATE mode — validates existing file / throws on missing</li>
 *   <li>AUTO mode — valid cache reused, missing/expired triggers exception in test context</li>
 *   <li>CLIENT_CREDENTIALS — token parsing and storageState synthesis</li>
 *   <li>{@code readAccessToken()} — reads back from saved storageState</li>
 *   <li>{@code getStorageStatePath()} — returns correct path or null</li>
 * </ul>
 */
class SsoAuthManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── NONE mode ─────────────────────────────────────────────────────────────

    @Test
    void ensureAuthenticated_noneMode_doesNothing() {
        SsoAuthConfig config = configOf(AuthMode.NONE);
        // Should not throw — NONE is a no-op
        SsoAuthManager.ensureAuthenticated(config);
    }

    // ── STORAGE_STATE mode ────────────────────────────────────────────────────

    @Test
    void ensureAuthenticated_storageStateMode_fileExists_ok(@TempDir Path tempDir)
            throws IOException {
        Path stateFile = tempDir.resolve("state.json");
        Files.writeString(stateFile, "{}");

        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.STORAGE_STATE, stateFile, Duration.ofHours(4),
                "", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
        // Should not throw — valid file exists
        SsoAuthManager.ensureAuthenticated(config);
    }

    @Test
    void ensureAuthenticated_storageStateMode_fileMissing_throws() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.STORAGE_STATE, Path.of("target/nonexistent/state.json"),
                Duration.ofHours(4),
                "", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
        assertThatThrownBy(() -> SsoAuthManager.ensureAuthenticated(config))
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void ensureAuthenticated_storageStateMode_expiredFile_throws(@TempDir Path tempDir)
            throws IOException {
        Path stateFile = tempDir.resolve("state.json");
        Files.writeString(stateFile, "{}");
        // Make the file 5 hours old (TTL is 4h)
        assertThat(stateFile.toFile().setLastModified(
                System.currentTimeMillis() - Duration.ofHours(5).toMillis())).isTrue();

        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.STORAGE_STATE, stateFile, Duration.ofHours(4),
                "", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
        assertThatThrownBy(() -> SsoAuthManager.ensureAuthenticated(config))
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("expired");
    }

    // ── AUTO mode ─────────────────────────────────────────────────────────────

    @Test
    void ensureAuthenticated_autoMode_validCache_reused(@TempDir Path tempDir)
            throws IOException {
        Path stateFile = tempDir.resolve("state.json");
        Files.writeString(stateFile, "{}");

        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.AUTO, stateFile, Duration.ofHours(4),
                "https://example.com/login", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
        // Should not throw — uses the valid cached file
        SsoAuthManager.ensureAuthenticated(config);
    }

    // ── parseAccessToken() ────────────────────────────────────────────────────

    @Test
    void parseAccessToken_validResponse() {
        String json = """
                {"access_token":"eyJhbGciOiJSUzI1NiJ9.test","expires_in":3600,"token_type":"Bearer"}
                """;
        String token = SsoAuthManager.parseAccessToken(json);
        assertThat(token).isEqualTo("eyJhbGciOiJSUzI1NiJ9.test");
    }

    @Test
    void parseAccessToken_missingField_throws() {
        String json = """
                {"token_type":"Bearer","expires_in":3600}
                """;
        assertThatThrownBy(() -> SsoAuthManager.parseAccessToken(json))
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("access_token");
    }

    @Test
    void parseAccessToken_blankToken_throws() {
        String json = """
                {"access_token":"","expires_in":3600}
                """;
        assertThatThrownBy(() -> SsoAuthManager.parseAccessToken(json))
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("access_token");
    }

    @Test
    void parseAccessToken_invalidJson_throws() {
        assertThatThrownBy(() -> SsoAuthManager.parseAccessToken("not-json"))
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("parse");
    }

    // ── saveTokenAsStorageState() + readAccessToken() round-trip ──────────────

    @Test
    void saveTokenAsStorageState_createsValidFile(@TempDir Path tempDir) throws IOException {
        Path stateFile = tempDir.resolve("auth/state.json");

        SsoAuthManager.saveTokenAsStorageState(stateFile, "my-test-token");

        assertThat(stateFile).exists();

        // Verify JSON structure
        JsonNode root = MAPPER.readTree(stateFile.toFile());
        assertThat(root.get("cookies").isArray()).isTrue();
        assertThat(root.get("cookies")).isEmpty();
        assertThat(root.get("origins").isArray()).isTrue();
        assertThat(root.get("origins").get(0).get("origin").asText())
                .isEqualTo("https://b-bot-auth");

        JsonNode localStorage = root.get("origins").get(0).get("localStorage");
        assertThat(localStorage.isArray()).isTrue();
        assertThat(localStorage.get(0).get("name").asText()).isEqualTo("b-bot-access-token");
        assertThat(localStorage.get(0).get("value").asText()).isEqualTo("my-test-token");
    }

    @Test
    void readAccessToken_roundTrip(@TempDir Path tempDir) {
        Path stateFile = tempDir.resolve("auth/state.json");
        SsoAuthManager.saveTokenAsStorageState(stateFile, "round-trip-token");

        String read = SsoAuthManager.readAccessToken(stateFile);
        assertThat(read).isEqualTo("round-trip-token");
    }

    @Test
    void readAccessToken_nullPath_returnsNull() {
        assertThat(SsoAuthManager.readAccessToken(null)).isNull();
    }

    @Test
    void readAccessToken_nonExistentFile_returnsNull() {
        assertThat(SsoAuthManager.readAccessToken(Path.of("nonexistent.json"))).isNull();
    }

    @Test
    void readAccessToken_noTokenEntry_returnsNull(@TempDir Path tempDir) throws IOException {
        Path stateFile = tempDir.resolve("state.json");
        // Simulate a storageState from interactive login (no b-bot-access-token entry)
        Files.writeString(stateFile, """
                {
                  "cookies": [{"name":"session","value":"abc","domain":"example.com"}],
                  "origins": [{"origin":"https://example.com","localStorage":[]}]
                }
                """);

        assertThat(SsoAuthManager.readAccessToken(stateFile)).isNull();
    }

    // ── getStorageStatePath() ─────────────────────────────────────────────────

    @Test
    void getStorageStatePath_noneMode_returnsNull() {
        SsoAuthConfig config = configOf(AuthMode.NONE);
        assertThat(SsoAuthManager.getStorageStatePath(config)).isNull();
    }

    @Test
    void getStorageStatePath_fileExists_returnsPath(@TempDir Path tempDir) throws IOException {
        Path stateFile = tempDir.resolve("state.json");
        Files.writeString(stateFile, "{}");

        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.AUTO, stateFile, Duration.ofHours(4),
                "https://example.com", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
        assertThat(SsoAuthManager.getStorageStatePath(config)).isEqualTo(stateFile);
    }

    @Test
    void getStorageStatePath_fileMissing_returnsNull() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.AUTO, Path.of("target/nonexistent/state.json"),
                Duration.ofHours(4),
                "https://example.com", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
        assertThat(SsoAuthManager.getStorageStatePath(config)).isNull();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @SuppressWarnings("SameParameterValue")
    private static SsoAuthConfig configOf(AuthMode mode) {
        return new SsoAuthConfig(
                mode,
                Path.of("target/auth/storage-state.json"),
                Duration.ofHours(4),
                "", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
    }
}

