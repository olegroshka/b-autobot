package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppContextTest {

    private static BBotConfig configWithBlotter(String webUrl, String apiBase) {
        return BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps.blotter.webUrl",  webUrl,
            "b-bot.apps.blotter.apiBase", apiBase
        ));
    }

    @Test
    void fromConfigReadsWebUrl() {
        BBotConfig cfg = configWithBlotter("http://localhost:9001/blotter/", "http://localhost:9001");
        AppContext ctx = AppContext.fromConfig("blotter", cfg);
        assertThat(ctx.getWebUrl()).isEqualTo("http://localhost:9001/blotter/");
    }

    @Test
    void fromConfigReadsApiBase() {
        BBotConfig cfg = configWithBlotter("http://localhost:9001/blotter/", "http://localhost:9001");
        AppContext ctx = AppContext.fromConfig("blotter", cfg);
        assertThat(ctx.getApiBaseUrl()).isEqualTo("http://localhost:9001");
    }

    @Test
    void fromConfigReadsUsers() {
        BBotConfig cfg = BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps.myapp.webUrl",         "http://localhost:9999/",
            "b-bot.apps.myapp.apiBase",        "http://localhost:9999",
            "b-bot.apps.myapp.users.trader",   "doej",
            "b-bot.apps.myapp.users.admin",    "smithj"
        ));
        AppContext ctx = AppContext.fromConfig("myapp", cfg);
        assertThat(ctx.getUser("trader")).hasValue("doej");
        assertThat(ctx.getUser("admin")).hasValue("smithj");
    }

    @Test
    void fromConfigEmptyWhenKeyMissing() {
        BBotConfig cfg = BBotConfig.load();
        AppContext ctx = AppContext.fromConfig("nonexistent-app", cfg);
        assertThat(ctx.getWebUrl()).isNull();
        assertThat(ctx.getApiBaseUrl()).isNull();
        assertThat(ctx.getUser("anyone")).isEmpty();
        assertThat(ctx.getExpectedVersion("any-service")).isEmpty();
    }

    @Test
    void webUrlMustEndWithSlash() {
        BBotConfig cfg = BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps.bad.webUrl",  "http://localhost:9001/noslash",
            "b-bot.apps.bad.apiBase", "http://localhost:9001"
        ));
        assertThatThrownBy(() -> AppContext.fromConfig("bad", cfg))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("webUrl must end with '/'");
    }

    @Test
    void apiBaseMustNotEndWithSlash() {
        BBotConfig cfg = BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps.bad.webUrl",  "http://localhost:9001/",
            "b-bot.apps.bad.apiBase", "http://localhost:9001/"
        ));
        assertThatThrownBy(() -> AppContext.fromConfig("bad", cfg))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("apiBase must NOT end with '/'");
    }

    @Test
    void getOtherAppApiBase_readsFromConfig() {
        BBotConfig cfg = BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps.blotter.webUrl",          "http://localhost:9001/blotter/",
            "b-bot.apps.blotter.apiBase",         "http://localhost:9001",
            "b-bot.apps.config-service.apiBase",  "http://localhost:9002"
        ));
        AppContext ctx = AppContext.fromConfig("blotter", cfg);
        assertThat(ctx.getOtherAppApiBase("config-service")).isEqualTo("http://localhost:9002");
    }
}
