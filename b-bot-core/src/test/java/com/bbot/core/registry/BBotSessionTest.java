package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.data.TestDataParser;
import com.bbot.core.exception.BBotConfigException;
import com.bbot.core.exception.BBotHealthCheckException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BBotSession} — immutability, builder lifecycle,
 * DSL factory delegation, health/version checks.
 *
 * <p>All descriptor registration uses {@code register(name, descriptor)} since
 * {@link AppDescriptor} no longer carries a {@code name()} method.
 * Health/version paths live in config overrides, not in descriptors.
 */
class BBotSessionTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Minimal descriptor — only dslFactory() is required. */
    private static AppDescriptor<String> descriptor(String name) {
        return () -> (ctx, page) -> "dsl-for-" + name;
    }

    private static BBotConfig configWithApp(String appName, String apiBase) {
        return BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps." + appName + ".apiBase", apiBase
        ));
    }

    // ── Builder lifecycle ─────────────────────────────────────────────────────

    @Test
    void builder_producesImmutableSession() {
        BBotSession session = BBotSession.builder()
            .register("app1", descriptor("app1"))
            .initialize(BBotConfig.load())
            .build();

        assertThat(session).isNotNull();
        assertThat(session.getConfig()).isNotNull();
        assertThat(session.appNames()).containsExactly("app1");
    }

    @Test
    void builder_buildWithoutInitialize_throws() {
        BBotSession.Builder builder = BBotSession.builder()
            .register("app1", descriptor("app1"));

        assertThatThrownBy(builder::build)
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("initialize");
    }

    @Test
    void builder_doubleInitialize_throws() {
        BBotSession.Builder builder = BBotSession.builder()
            .register("app1", descriptor("app1"))
            .initialize(BBotConfig.load());

        assertThatThrownBy(() -> builder.initialize(BBotConfig.load()))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("already called");
    }

    @Test
    void builder_doubleBuild_throws() {
        BBotSession.Builder builder = BBotSession.builder()
            .register("app1", descriptor("app1"))
            .initialize(BBotConfig.load());

        builder.build(); // first call OK

        assertThatThrownBy(builder::build)
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("already called");
    }

    @Test
    void builder_registerAfterBuild_throws() {
        BBotSession.Builder builder = BBotSession.builder()
            .register("app1", descriptor("app1"))
            .initialize(BBotConfig.load());
        builder.build();

        assertThatThrownBy(() -> builder.register("app2", descriptor("app2")))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("after build");
    }

    @Test
    void builder_nullConfig_throws() {
        assertThatThrownBy(() -> BBotSession.builder().initialize(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builder_nullDescriptor_throws() {
        assertThatThrownBy(() -> BBotSession.builder().register("app1", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builder_nullName_throws() {
        assertThatThrownBy(() -> BBotSession.builder().register(null, descriptor("x")))
            .isInstanceOf(NullPointerException.class);
    }

    // ── DSL factory ───────────────────────────────────────────────────────────

    @Test
    void dsl_callsFactory() {
        BBotSession session = BBotSession.builder()
            .register("myapp", descriptor("myapp"))
            .initialize(BBotConfig.load())
            .build();

        String result = session.dsl("myapp", null, String.class);
        assertThat(result).isEqualTo("dsl-for-myapp");
    }

    @Test
    void dsl_unknownApp_throws() {
        BBotSession session = BBotSession.builder()
            .register("known", descriptor("known"))
            .initialize(BBotConfig.load())
            .build();

        assertThatThrownBy(() -> session.dsl("nonexistent", null, String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nonexistent")
            .hasMessageContaining("known");
    }

    @Test
    void dsl_multipleApps() {
        BBotSession session = BBotSession.builder()
            .register("app1", descriptor("app1"))
            .register("app2", descriptor("app2"))
            .initialize(BBotConfig.load())
            .build();

        assertThat(session.dsl("app1", null, String.class)).isEqualTo("dsl-for-app1");
        assertThat(session.dsl("app2", null, String.class)).isEqualTo("dsl-for-app2");
        assertThat(session.appNames()).containsExactly("app1", "app2");
    }

    // ── Health / version — paths come from config overrides ───────────────────

    @Test
    void checkHealth_noPathIsNoOp() {
        // No health-check-action in config → no-op (no HTTP call, no exception)
        BBotSession session = BBotSession.builder()
            .register("no-health", descriptor("no-health"))
            .initialize(configWithApp("no-health", "http://localhost:1"))
            .build();

        session.checkHealth("no-health"); // no exception
    }

    @Test
    void checkHealth_succeeds2xx() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            BBotSession session = BBotSession.builder()
                .register("svc", descriptor("svc"))
                .initialize(BBotConfig.load().withOverrides(Map.of(
                    "b-bot.apps.svc.apiBase",                         "http://localhost:" + port,
                    "b-bot.apps.svc.api-actions.health-check.method", "GET",
                    "b-bot.apps.svc.api-actions.health-check.path",   "/health",
                    "b-bot.apps.svc.health-check-action",             "health-check"
                )))
                .build();

            session.checkHealth("svc"); // no exception
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checkHealth_throws5xx() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            BBotSession session = BBotSession.builder()
                .register("failing", descriptor("failing"))
                .initialize(BBotConfig.load().withOverrides(Map.of(
                    "b-bot.apps.failing.apiBase",                         "http://localhost:" + port,
                    "b-bot.apps.failing.api-actions.health-check.method", "GET",
                    "b-bot.apps.failing.api-actions.health-check.path",   "/health",
                    "b-bot.apps.failing.health-check-action",             "health-check"
                )))
                .build();

            assertThatThrownBy(() -> session.checkHealth("failing"))
                .isInstanceOf(BBotHealthCheckException.class)
                .hasMessageContaining("503");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void assertVersion_matches() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/version", exchange -> {
            byte[] body = "{\"version\":\"v2.4.1\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            BBotSession session = BBotSession.builder()
                .register("svc", descriptor("svc"))
                .initialize(BBotConfig.load().withOverrides(Map.of(
                    "b-bot.apps.svc.apiBase",      "http://localhost:" + port,
                    "b-bot.apps.svc.version-path", "/version"
                )))
                .build();

            session.assertVersion("svc", "v2.4.1"); // no exception
        } finally {
            server.stop(0);
        }
    }

    @Test
    void assertVersion_mismatch() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/version", exchange -> {
            byte[] body = "{\"version\":\"v1.0.0\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            BBotSession session = BBotSession.builder()
                .register("svc", descriptor("svc"))
                .initialize(BBotConfig.load().withOverrides(Map.of(
                    "b-bot.apps.svc.apiBase",      "http://localhost:" + port,
                    "b-bot.apps.svc.version-path", "/version"
                )))
                .build();

            assertThatThrownBy(() -> session.assertVersion("svc", "v2.4.1"))
                .isInstanceOf(BBotHealthCheckException.class)
                .hasMessageContaining("v2.4.1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checkAllHealth_delegatesToEachApp() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            String apiBase = "http://localhost:" + port;
            BBotSession session = BBotSession.builder()
                .register("app1", descriptor("app1"))
                .register("app2", descriptor("app2")) // no health-check-action → no-op
                .initialize(BBotConfig.load().withOverrides(Map.of(
                    "b-bot.apps.app1.apiBase",                         apiBase,
                    "b-bot.apps.app1.api-actions.health-check.method", "GET",
                    "b-bot.apps.app1.api-actions.health-check.path",   "/health",
                    "b-bot.apps.app1.health-check-action",             "health-check",
                    "b-bot.apps.app2.apiBase",                         apiBase
                )))
                .build();

            session.checkAllHealth(); // no exception
        } finally {
            server.stop(0);
        }
    }

    // ── TestDataParser ────────────────────────────────────────────────────────

    @Test
    void build_invokesTestDataParser() {
        AppDescriptor<String> desc = new AppDescriptor<String>() {
            @Override public DslFactory<String> dslFactory() { return (ctx, page) -> "dsl"; }
            @Override public TestDataParser<?> testDataParser() { return cfg -> "parsed-data"; }
        };

        BBotSession session = BBotSession.builder()
            .register("app1", desc)
            .initialize(BBotConfig.load())
            .build();

        assertThat(session.context("app1").getTestData(String.class)).isEqualTo("parsed-data");
    }

    @Test
    void build_skipsNullParser() {
        // Default AppDescriptor returns null from testDataParser() → parsedTestData stays null
        BBotSession session = BBotSession.builder()
            .register("app1", descriptor("app1"))
            .initialize(BBotConfig.load())
            .build();

        assertThat(session.context("app1").getTestData(String.class)).isNull();
    }

    // ── Auto-discovery ────────────────────────────────────────────────────────

    @Test
    void initialize_autoDiscoversDescriptorClass() {
        // Use a class that actually exists on the test classpath:
        // java.util.function.Supplier as a stand-in is not AppDescriptor, so we need
        // a real AppDescriptor on the classpath.  We use the test's own anonymous class
        // indirectly by pre-registering a sentinel and ensuring auto-discovery skips it.
        BBotSession session = BBotSession.builder()
            .register("blotter", descriptor("blotter")) // explicit wins
            .initialize(BBotConfig.load())              // blotter has no descriptor-class in test conf
            .build();

        // Should still work — explicit registration kept
        assertThat(session.appNames()).contains("blotter");
        assertThat(session.dsl("blotter", null, String.class)).isEqualTo("dsl-for-blotter");
    }

    @Test
    void initialize_autoDiscovery_invalidFqcn_throws() {
        assertThatThrownBy(() ->
            BBotSession.builder()
                .initialize(BBotConfig.load().withOverrides(Map.of(
                    "b-bot.apps.badapp.descriptor-class", "com.example.DoesNotExist"
                )))
                .build()
        )
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("DoesNotExist");
    }

    // ── Context accessor ──────────────────────────────────────────────────────

    @Test
    void context_returnsResolvedAppContext() {
        BBotSession session = BBotSession.builder()
            .register("myapp", descriptor("myapp"))
            .initialize(configWithApp("myapp", "http://example.com"))
            .build();

        AppContext ctx = session.context("myapp");
        assertThat(ctx.name()).isEqualTo("myapp");
        assertThat(ctx.getApiBaseUrl()).isEqualTo("http://example.com");
    }

    @Test
    void context_unknownApp_throws() {
        BBotSession session = BBotSession.builder()
            .register("myapp", descriptor("myapp"))
            .initialize(BBotConfig.load())
            .build();

        assertThatThrownBy(() -> session.context("unknown"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unknown");
    }
}
