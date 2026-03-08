package com.bbot.core.auth;

import com.bbot.core.auth.SsoAuthConfig.AuthMode;
import com.bbot.core.config.BBotConfig;
import com.bbot.core.exception.BBotAuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SsoAuthConfig}.
 */
class SsoAuthConfigTest {

    // ── AuthMode.parse ──────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "none,              NONE",
            "interactive,       INTERACTIVE",
            "storageState,      STORAGE_STATE",
            "auto,              AUTO",
            "clientCredentials, CLIENT_CREDENTIALS",
            "NONE,              NONE",
            "Interactive,       INTERACTIVE",
            "STORAGESTATE,      STORAGE_STATE",
            "AUTO,              AUTO",
            "CLIENTCREDENTIALS, CLIENT_CREDENTIALS"
    })
    void parseMode_validValues(String input, AuthMode expected) {
        assertThat(AuthMode.parse(input)).isEqualTo(expected);
    }

    @Test
    void parseMode_unknownValue_throws() {
        assertThatThrownBy(() -> AuthMode.parse("kerberos"))
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("kerberos")
                .hasMessageContaining("Valid values");
    }

    // ── SsoAuthConfig.from() with defaults ──────────────────────────────────

    @Test
    void fromDefaultConfig_returnsNoneMode() {
        BBotConfig cfg = BBotConfig.load();
        SsoAuthConfig auth = SsoAuthConfig.from(cfg);

        assertThat(auth.mode()).isEqualTo(AuthMode.NONE);
        assertThat(auth.isNone()).isTrue();
        assertThat(auth.storageStatePath()).isEqualTo(Path.of("target/auth/storage-state.json"));
        assertThat(auth.sessionTtl()).isEqualTo(Duration.ofHours(4));
        assertThat(auth.loginUrl()).isEmpty();
        assertThat(auth.loginTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(auth.loginSuccessIndicator()).isEqualTo("pause");
        assertThat(auth.tokenUrl()).isEmpty();
        assertThat(auth.clientId()).isEmpty();
        assertThat(auth.clientSecret()).isEmpty();
        assertThat(auth.scope()).isEmpty();
        assertThat(auth.refreshOn401()).isTrue();
    }

    @Test
    void fromDefaultConfig_usesStorageState_isFalseForNone() {
        BBotConfig cfg = BBotConfig.load();
        SsoAuthConfig auth = SsoAuthConfig.from(cfg);

        assertThat(auth.usesStorageState()).isFalse();
    }

    // ── getSsoAuthConfig() accessor on BBotConfig ───────────────────────────

    @Test
    void bbotConfig_getSsoAuthConfig_returnsSameAsFrom() {
        BBotConfig cfg = BBotConfig.load();
        SsoAuthConfig viaAccessor = cfg.getSsoAuthConfig();
        SsoAuthConfig viaDirect   = SsoAuthConfig.from(cfg);

        assertThat(viaAccessor.mode()).isEqualTo(viaDirect.mode());
        assertThat(viaAccessor.storageStatePath()).isEqualTo(viaDirect.storageStatePath());
        assertThat(viaAccessor.sessionTtl()).isEqualTo(viaDirect.sessionTtl());
    }

    // ── isStorageStateValid() ───────────────────────────────────────────────

    @Test
    void isStorageStateValid_fileMissing_returnsFalse() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.AUTO,
                Path.of("target/nonexistent/file.json"),
                Duration.ofHours(4),
                "", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
        assertThat(config.isStorageStateValid()).isFalse();
    }

    @Test
    void isStorageStateValid_freshFile_returnsTrue(@TempDir Path tempDir) throws IOException {
        Path stateFile = tempDir.resolve("state.json");
        Files.writeString(stateFile, "{}");

        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.AUTO,
                stateFile,
                Duration.ofHours(4),
                "", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
        assertThat(config.isStorageStateValid()).isTrue();
    }

    @Test
    void isStorageStateValid_expiredFile_returnsFalse(@TempDir Path tempDir) throws IOException {
        Path stateFile = tempDir.resolve("state.json");
        Files.writeString(stateFile, "{}");
        // Set last modified time to 5 hours ago (TTL is 4h)
        assertThat(stateFile.toFile().setLastModified(
                Instant.now().minus(5, ChronoUnit.HOURS).toEpochMilli())).isTrue();

        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.AUTO,
                stateFile,
                Duration.ofHours(4),
                "", Duration.ofSeconds(120), "pause",
                "", "", "", "", true
        );
        assertThat(config.isStorageStateValid()).isFalse();
    }

    // ── usesStorageState() ──────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"interactive", "storageState", "auto"})
    void usesStorageState_trueModes(String modeStr) {
        AuthMode mode = AuthMode.parse(modeStr);
        SsoAuthConfig config = new SsoAuthConfig(
                mode, Path.of("x.json"), Duration.ofHours(1),
                "http://example.com", Duration.ofSeconds(60), "pause",
                "", "", "", "", true
        );
        assertThat(config.usesStorageState()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "clientCredentials"})
    void usesStorageState_falseModes(String modeStr) {
        AuthMode mode = AuthMode.parse(modeStr);
        SsoAuthConfig config = new SsoAuthConfig(
                mode, Path.of("x.json"), Duration.ofHours(1),
                "", Duration.ofSeconds(60), "pause",
                "", "", "", "", true
        );
        assertThat(config.usesStorageState()).isFalse();
    }

    // ── validate() ──────────────────────────────────────────────────────────

    @Test
    void validate_noneMode_neverThrows() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.NONE, Path.of("x.json"), Duration.ofHours(1),
                "", Duration.ofSeconds(60), "pause",
                "", "", "", "", true
        );
        config.validate(); // should not throw
    }

    @Test
    void validate_interactiveMode_requiresLoginUrl() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.INTERACTIVE, Path.of("x.json"), Duration.ofHours(1),
                "", Duration.ofSeconds(60), "pause",
                "", "", "", "", true
        );
        assertThatThrownBy(config::validate)
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("loginUrl")
                .hasMessageContaining("INTERACTIVE");
    }

    @Test
    void validate_interactiveMode_withLoginUrl_ok() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.INTERACTIVE, Path.of("x.json"), Duration.ofHours(1),
                "https://app.company.com/", Duration.ofSeconds(60), "pause",
                "", "", "", "", true
        );
        config.validate(); // should not throw
    }

    @Test
    void validate_autoMode_requiresLoginUrl() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.AUTO, Path.of("x.json"), Duration.ofHours(1),
                "  ", Duration.ofSeconds(60), "pause",
                "", "", "", "", true
        );
        assertThatThrownBy(config::validate)
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("loginUrl");
    }

    @Test
    void validate_storageStateMode_requiresFileExists(@TempDir Path tempDir) {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.STORAGE_STATE,
                tempDir.resolve("does-not-exist.json"),
                Duration.ofHours(1),
                "", Duration.ofSeconds(60), "pause",
                "", "", "", "", true
        );
        assertThatThrownBy(config::validate)
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("does not exist")
                .hasMessageContaining("interactive");
    }

    @Test
    void validate_storageStateMode_fileExists_ok(@TempDir Path tempDir) throws IOException {
        Path stateFile = tempDir.resolve("state.json");
        Files.writeString(stateFile, "{}");

        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.STORAGE_STATE, stateFile, Duration.ofHours(1),
                "", Duration.ofSeconds(60), "pause",
                "", "", "", "", true
        );
        config.validate(); // should not throw
    }

    @Test
    void validate_clientCredentials_requiresTokenUrl() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.CLIENT_CREDENTIALS, Path.of("x.json"), Duration.ofHours(1),
                "", Duration.ofSeconds(60), "pause",
                "", "client-id", "client-secret", "scope", true
        );
        assertThatThrownBy(config::validate)
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("tokenUrl");
    }

    @Test
    void validate_clientCredentials_requiresClientId() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.CLIENT_CREDENTIALS, Path.of("x.json"), Duration.ofHours(1),
                "", Duration.ofSeconds(60), "pause",
                "https://token.url/oauth2/token", "", "secret", "scope", true
        );
        assertThatThrownBy(config::validate)
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("clientId");
    }

    @Test
    void validate_clientCredentials_requiresClientSecret() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.CLIENT_CREDENTIALS, Path.of("x.json"), Duration.ofHours(1),
                "", Duration.ofSeconds(60), "pause",
                "https://token.url/oauth2/token", "my-id", "", "scope", true
        );
        assertThatThrownBy(config::validate)
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("clientSecret");
    }

    @Test
    void validate_clientCredentials_allPresent_ok() {
        SsoAuthConfig config = new SsoAuthConfig(
                AuthMode.CLIENT_CREDENTIALS, Path.of("x.json"), Duration.ofHours(1),
                "", Duration.ofSeconds(60), "pause",
                "https://token.url/oauth2/token", "my-id", "my-secret", "api://.default", true
        );
        config.validate(); // should not throw
    }
}

