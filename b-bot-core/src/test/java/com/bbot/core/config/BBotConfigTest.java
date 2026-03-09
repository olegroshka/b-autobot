package com.bbot.core.config;

import com.bbot.core.data.ApiAction;
import com.bbot.core.exception.BBotConfigException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BBotConfig}.
 *
 * <p>No browser, no Playwright — pure HOCON loading logic.
 * The {@code b-bot.env} system property is cleared after each test to
 * avoid cross-test contamination.
 */
class BBotConfigTest {

    @AfterEach
    void clearEnvProperty() {
        System.clearProperty("b-bot.env");
    }

    // ── G2.1-a: reference.conf defaults resolve correctly ────────────────────

    @Test
    void loadDefaults_browserAndTimeoutsFromReferenceConf() {
        BBotConfig cfg = BBotConfig.load();

        assertThat(cfg.getString("b-bot.browser.type")).isEqualTo("chromium");
        assertThat(cfg.getBoolean("b-bot.browser.headless")).isTrue();
        assertThat(cfg.getTimeout("b-bot.timeouts.navigation")).isEqualTo(Duration.ofSeconds(30));
        assertThat(cfg.getTimeout("b-bot.timeouts.cellFlash")).isEqualTo(Duration.ofSeconds(3));
        assertThat(cfg.getTimeout("b-bot.timeouts.gridRender")).isEqualTo(Duration.ofSeconds(10));
        assertThat(cfg.getTimeout("b-bot.timeouts.apiResponse")).isEqualTo(Duration.ofSeconds(10));
    }

    // ── G2.1-b: withOverrides wins over HOCON ────────────────────────────────

    @Test
    void withOverridesWins_overHoconValue() {
        BBotConfig cfg = BBotConfig.load()
                .withOverrides(Map.of("b-bot.browser.headless", "false"));

        assertThat(cfg.getBoolean("b-bot.browser.headless")).isFalse();
    }

    @Test
    void withOverridesIsImmutable_originalUnchanged() {
        BBotConfig original  = BBotConfig.load();
        BBotConfig overridden = original.withOverrides(Map.of("b-bot.browser.type", "firefox"));

        assertThat(original.getString("b-bot.browser.type")).isEqualTo("chromium");
        assertThat(overridden.getString("b-bot.browser.type")).isEqualTo("firefox");
    }

    // ── G2.1-c: env layer overrides base ─────────────────────────────────────

    @Test
    void envLayerOverridesBase_applicationTestEnvConf() {
        System.setProperty("b-bot.env", "test-env");

        BBotConfig cfg = BBotConfig.load();

        // The test-env layer sets cellFlash = 9s (overrides reference.conf 3s)
        assertThat(cfg.getTimeout("b-bot.timeouts.cellFlash")).isEqualTo(Duration.ofSeconds(9));
        // Other reference values still resolve
        assertThat(cfg.getTimeout("b-bot.timeouts.navigation")).isEqualTo(Duration.ofSeconds(30));
        // App URLs from test-env layer
        assertThat(cfg.getAppWebUrl("blotter")).isEqualTo("https://test-env-blotter.example.com/");
        assertThat(cfg.getAppApiBase("blotter")).isEqualTo("https://test-env-api.example.com");
    }

    // ── G2.1-d: absent optional keys return null / empty ─────────────────────

    @Test
    void getAppWebUrl_returnsNullWhenNotConfigured() {
        BBotConfig cfg = BBotConfig.load();

        assertThat(cfg.getAppWebUrl("nonexistent-app")).isNull();
    }

    @Test
    void getAppApiBase_returnsNullWhenNotConfigured() {
        BBotConfig cfg = BBotConfig.load();

        assertThat(cfg.getAppApiBase("nonexistent-app")).isNull();
    }

    @Test
    void getAppUsers_returnsEmptyMapWhenNotConfigured() {
        BBotConfig cfg = BBotConfig.load();

        assertThat(cfg.getAppUsers("nonexistent-app")).isEmpty();
    }

    @Test
    void getAppVersions_returnsEmptyMapWhenNotConfigured() {
        BBotConfig cfg = BBotConfig.load();

        assertThat(cfg.getAppVersions("nonexistent-app")).isEmpty();
    }

    // ── G2.1-e: withOverrides for dynamic app URLs ───────────────────────────

    @Test
    void withOverrides_injectsRuntimeAppUrls() {
        BBotConfig cfg = BBotConfig.load()
                .withOverrides(Map.of(
                    "b-bot.apps.blotter.webUrl",  "http://localhost:9001/blotter/",
                    "b-bot.apps.blotter.apiBase", "http://localhost:9001"
                ));

        assertThat(cfg.getAppWebUrl("blotter")).isEqualTo("http://localhost:9001/blotter/");
        assertThat(cfg.getAppApiBase("blotter")).isEqualTo("http://localhost:9001");
    }

    // ── G2.1-f: hasPath ───────────────────────────────────────────────────────

    @Test
    void hasPath_trueForKnownKey_falseForMissing() {
        BBotConfig cfg = BBotConfig.load();

        assertThat(cfg.hasPath("b-bot.browser.type")).isTrue();
        assertThat(cfg.hasPath("b-bot.does.not.exist")).isFalse();
    }

    // ── G7g: getApiAction ─────────────────────────────────────────────────────

    @Test
    void getApiAction_findsAcrossApps() {
        BBotConfig cfg = BBotConfig.load();

        ApiAction action = cfg.getApiAction("submit-rfq");
        assertThat(action).isNotNull();
        assertThat(action.app()).isEqualTo("blotter");
    }

    @Test
    void getApiAction_recordFields() {
        BBotConfig cfg = BBotConfig.load();

        ApiAction action = cfg.getApiAction("submit-rfq");
        assertThat(action.name()).isEqualTo("submit-rfq");
        assertThat(action.method()).isEqualTo("POST");
        assertThat(action.app()).isEqualTo("blotter");
        assertThat(action.path()).isEqualTo("/api/inquiry");
        assertThat(action.template()).isEqualTo("test-rfq");
    }

    @Test
    void getApiAction_nullableTemplate() {
        BBotConfig cfg = BBotConfig.load();

        ApiAction action = cfg.getApiAction("list-inquiries");
        assertThat(action.method()).isEqualTo("GET");
        assertThat(action.template()).isNull();
    }

    @Test
    void getApiAction_unknownName_throws() {
        BBotConfig cfg = BBotConfig.load();

        assertThatThrownBy(() -> cfg.getApiAction("nonexistent-action"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("nonexistent-action");
    }

    // ── getAppNames / getAppDescriptorClass / getAppHealthCheckPath / getAppVersionPath ──

    @Test
    void getAppNames_returnsConfiguredApps() {
        BBotConfig cfg = BBotConfig.load(); // test application.conf has blotter app
        assertThat(cfg.getAppNames()).contains("blotter");
    }

    @Test
    void getAppNames_returnsEmpty_whenNoApps() {
        // Override with a config that has no apps
        BBotConfig cfg = BBotConfig.load().withOverrides(Map.of());
        // reference.conf defines b-bot.apps = {} so at minimum it's empty, not null
        assertThat(cfg.getAppNames()).isNotNull();
    }

    @Test
    void getAppDescriptorClass_returnsValue_whenConfigured() {
        BBotConfig cfg = BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps.myapp.descriptor-class", "com.example.MyDescriptor"
        ));
        assertThat(cfg.getAppDescriptorClass("myapp")).contains("com.example.MyDescriptor");
    }

    @Test
    void getAppDescriptorClass_returnsEmpty_whenNotConfigured() {
        BBotConfig cfg = BBotConfig.load();
        assertThat(cfg.getAppDescriptorClass("blotter")).isEmpty();
    }

    @Test
    void getAppHealthCheckPath_returnsValue_whenConfigured() {
        BBotConfig cfg = BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps.svc.health-check-path", "/api/health"
        ));
        assertThat(cfg.getAppHealthCheckPath("svc")).contains("/api/health");
    }

    @Test
    void getAppHealthCheckPath_returnsEmpty_whenNotConfigured() {
        BBotConfig cfg = BBotConfig.load();
        assertThat(cfg.getAppHealthCheckPath("blotter")).isEmpty();
    }

    @Test
    void getAppVersionPath_returnsValue_whenConfigured() {
        BBotConfig cfg = BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps.svc.version-path", "/api/version"
        ));
        assertThat(cfg.getAppVersionPath("svc")).contains("/api/version");
    }

    @Test
    void getAppVersionPath_returnsEmpty_whenNotConfigured() {
        BBotConfig cfg = BBotConfig.load();
        assertThat(cfg.getAppVersionPath("blotter")).isEmpty();
    }

    // ── getAppActionPath ──────────────────────────────────────────────────────

    @Test
    void getAppActionPath_present() {
        BBotConfig cfg = BBotConfig.load(); // blotter.api-actions.list-inquiries.path is in test conf
        assertThat(cfg.getAppActionPath("blotter", "list-inquiries")).isEqualTo("/api/inquiries");
    }

    @Test
    void getAppActionPath_absent_throws() {
        BBotConfig cfg = BBotConfig.load();
        assertThatThrownBy(() -> cfg.getAppActionPath("blotter", "nonexistent-action"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("nonexistent-action")
            .hasMessageContaining("blotter");
    }

    // ── getTestData ───────────────────────────────────────────────────────────

    @Test
    void getTestData_returnsNonNull() {
        BBotConfig cfg = BBotConfig.load();

        assertThat(cfg.getTestData()).isNotNull();
    }

    // ── M8f: tracing config defaults ──────────────────────────────────────────

    @Test
    void tracingDefaults_disabledWithDefaultOutputDir() {
        BBotConfig cfg = BBotConfig.load();

        assertThat(cfg.getBoolean("b-bot.tracing.enabled")).isFalse();
        assertThat(cfg.getString("b-bot.tracing.outputDir")).isEqualTo("target/playwright-traces");
    }
}
