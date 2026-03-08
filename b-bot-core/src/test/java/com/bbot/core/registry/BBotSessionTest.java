package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.exception.BBotConfigException;
import com.bbot.core.exception.BBotHealthCheckException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BBotSession} — immutability, builder lifecycle,
 * DSL factory delegation, health/version checks.
 */
class BBotSessionTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AppDescriptor<String> descriptor(String name) {
        return descriptor(name, Optional.empty(), Optional.empty());
    }

    private static AppDescriptor<String> descriptor(
            String name, Optional<String> healthPath,
            Optional<String> versionPath) {
        return new AppDescriptor<>() {
            @Override public String name() { return name; }
            @Override public Set<ComponentType> componentTypes() { return Set.of(ComponentType.REST_API); }
            @Override public DslFactory<String> dslFactory() {
                return (ctx, page) -> "dsl-for-" + name;
            }
            @Override public Optional<String> healthCheckPath() { return healthPath; }
            @Override public Optional<String> versionPath()     { return versionPath; }
        };
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
            .register(descriptor("app1"))
            .initialize(BBotConfig.load())
            .build();

        assertThat(session).isNotNull();
        assertThat(session.getConfig()).isNotNull();
        assertThat(session.appNames()).containsExactly("app1");
    }

    @Test
    void builder_buildWithoutInitialize_throws() {
        BBotSession.Builder builder = BBotSession.builder()
            .register(descriptor("app1"));

        assertThatThrownBy(builder::build)
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("initialize");
    }

    @Test
    void builder_doubleInitialize_throws() {
        BBotSession.Builder builder = BBotSession.builder()
            .register(descriptor("app1"))
            .initialize(BBotConfig.load());

        assertThatThrownBy(() -> builder.initialize(BBotConfig.load()))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("already called");
    }

    @Test
    void builder_doubleBuild_throws() {
        BBotSession.Builder builder = BBotSession.builder()
            .register(descriptor("app1"))
            .initialize(BBotConfig.load());

        builder.build(); // first call OK

        assertThatThrownBy(builder::build)
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("already called");
    }

    @Test
    void builder_registerAfterBuild_throws() {
        BBotSession.Builder builder = BBotSession.builder()
            .register(descriptor("app1"))
            .initialize(BBotConfig.load());
        builder.build();

        assertThatThrownBy(() -> builder.register(descriptor("app2")))
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
        assertThatThrownBy(() -> BBotSession.builder().register(null))
            .isInstanceOf(NullPointerException.class);
    }

    // ── DSL factory ───────────────────────────────────────────────────────────

    @Test
    void dsl_callsFactory() {
        BBotSession session = BBotSession.builder()
            .register(descriptor("myapp"))
            .initialize(BBotConfig.load())
            .build();

        String result = session.dsl("myapp", null, String.class);
        assertThat(result).isEqualTo("dsl-for-myapp");
    }

    @Test
    void dsl_unknownApp_throws() {
        BBotSession session = BBotSession.builder()
            .register(descriptor("known"))
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
            .register(descriptor("app1"))
            .register(descriptor("app2"))
            .initialize(BBotConfig.load())
            .build();

        assertThat(session.dsl("app1", null, String.class)).isEqualTo("dsl-for-app1");
        assertThat(session.dsl("app2", null, String.class)).isEqualTo("dsl-for-app2");
        assertThat(session.appNames()).containsExactly("app1", "app2");
    }

    // ── Health / version ──────────────────────────────────────────────────────

    @Test
    void checkHealth_noPathIsNoOp() {
        BBotSession session = BBotSession.builder()
            .register(descriptor("no-health"))
            .initialize(configWithApp("no-health", "http://localhost:1"))
            .build();

        // No exception = success (no-op because no healthCheckPath)
        session.checkHealth("no-health");
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
                .register(descriptor("svc", Optional.of("/health"), Optional.empty()))
                .initialize(configWithApp("svc", "http://localhost:" + port))
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
                .register(descriptor("failing", Optional.of("/health"), Optional.empty()))
                .initialize(configWithApp("failing", "http://localhost:" + port))
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
                .register(descriptor("svc", Optional.empty(), Optional.of("/version")))
                .initialize(configWithApp("svc", "http://localhost:" + port))
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
                .register(descriptor("svc", Optional.empty(), Optional.of("/version")))
                .initialize(configWithApp("svc", "http://localhost:" + port))
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
                .register(descriptor("app1", Optional.of("/health"), Optional.empty()))
                .register(descriptor("app2")) // no health path — no-op
                .initialize(BBotConfig.load().withOverrides(Map.of(
                    "b-bot.apps.app1.apiBase", apiBase,
                    "b-bot.apps.app2.apiBase", apiBase
                )))
                .build();

            session.checkAllHealth(); // no exception
        } finally {
            server.stop(0);
        }
    }

    // ── Context accessor ──────────────────────────────────────────────────────

    @Test
    void context_returnsResolvedAppContext() {
        BBotSession session = BBotSession.builder()
            .register(descriptor("myapp"))
            .initialize(configWithApp("myapp", "http://example.com"))
            .build();

        AppContext ctx = session.context("myapp");
        assertThat(ctx.name()).isEqualTo("myapp");
        assertThat(ctx.getApiBaseUrl()).isEqualTo("http://example.com");
    }

    @Test
    void context_unknownApp_throws() {
        BBotSession session = BBotSession.builder()
            .register(descriptor("myapp"))
            .initialize(BBotConfig.load())
            .build();

        assertThatThrownBy(() -> session.context("unknown"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unknown");
    }
}

