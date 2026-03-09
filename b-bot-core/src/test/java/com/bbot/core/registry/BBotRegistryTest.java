package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.exception.BBotHealthCheckException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BBotRegistryTest {

    @AfterEach
    void cleanup() {
        BBotRegistry.clearSession();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Minimal descriptor — only dslFactory() required. */
    private static AppDescriptor<String> descriptor(String name) {
        return () -> (ctx, page) -> "dsl-for-" + name;
    }

    private static BBotConfig configWithApp(String appName, String apiBase) {
        return BBotConfig.load().withOverrides(Map.of(
            "b-bot.apps." + appName + ".apiBase", apiBase
        ));
    }

    private static BBotSession sessionFor(String name) {
        return BBotSession.builder()
                .register(name, descriptor(name))
                .initialize(BBotConfig.load())
                .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void registerAndDslCallsFactory() {
        BBotRegistry.setSession(sessionFor("myapp"));

        String result = BBotRegistry.session().dsl("myapp", null, String.class);
        assertThat(result).isEqualTo("dsl-for-myapp");
    }

    @Test
    void unknownAppNameThrows() {
        BBotRegistry.setSession(sessionFor("known"));

        assertThatThrownBy(() -> BBotRegistry.session().dsl("nonexistent", null, String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nonexistent")
            .hasMessageContaining("known");
    }

    @Test
    void sessionBeforeSetSessionThrows() {
        // session never set → session() throws with actionable message
        assertThatThrownBy(BBotRegistry::session)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("initialize");
    }

    @Test
    void clearSessionClearsAll() {
        BBotRegistry.setSession(sessionFor("myapp"));

        BBotRegistry.clearSession();

        assertThatThrownBy(BBotRegistry::session)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void checkHealthNoOpWhenNoPath() {
        // No health-check-path in config → no-op
        BBotSession session = BBotSession.builder()
                .register("no-health", descriptor("no-health"))
                .initialize(configWithApp("no-health", "http://localhost:1"))
                .build();
        BBotRegistry.setSession(session);

        // Must complete without exception
        BBotRegistry.session().checkHealth("no-health");
    }

    @Test
    void checkHealthCallsEndpointAndSucceedsOn2xx() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger callCount = new AtomicInteger(0);
        server.createContext("/health", exchange -> {
            callCount.incrementAndGet();
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
                        "b-bot.apps.svc.apiBase",           "http://localhost:" + port,
                        "b-bot.apps.svc.health-check-path", "/health"
                    )))
                    .build();
            BBotRegistry.setSession(session);

            BBotRegistry.session().checkHealth("svc");

            assertThat(callCount.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checkHealthThrowsOn5xx() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            BBotSession session = BBotSession.builder()
                    .register("failing-svc", descriptor("failing-svc"))
                    .initialize(BBotConfig.load().withOverrides(Map.of(
                        "b-bot.apps.failing-svc.apiBase",           "http://localhost:" + port,
                        "b-bot.apps.failing-svc.health-check-path", "/health"
                    )))
                    .build();
            BBotRegistry.setSession(session);

            assertThatThrownBy(() -> BBotRegistry.session().checkHealth("failing-svc"))
                .isInstanceOf(BBotHealthCheckException.class)
                .hasMessageContaining("503");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void assertVersionMatchesJsonField() throws IOException {
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
                    .register("versioned-svc", descriptor("versioned-svc"))
                    .initialize(BBotConfig.load().withOverrides(Map.of(
                        "b-bot.apps.versioned-svc.apiBase",      "http://localhost:" + port,
                        "b-bot.apps.versioned-svc.version-path", "/version"
                    )))
                    .build();
            BBotRegistry.setSession(session);

            BBotRegistry.session().assertVersion("versioned-svc", "v2.4.1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void assertVersionThrowsOnMismatch() throws IOException {
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
                    .register("versioned-svc", descriptor("versioned-svc"))
                    .initialize(BBotConfig.load().withOverrides(Map.of(
                        "b-bot.apps.versioned-svc.apiBase",      "http://localhost:" + port,
                        "b-bot.apps.versioned-svc.version-path", "/version"
                    )))
                    .build();
            BBotRegistry.setSession(session);

            assertThatThrownBy(() -> BBotRegistry.session().assertVersion("versioned-svc", "v2.4.1"))
                .isInstanceOf(BBotHealthCheckException.class)
                .hasMessageContaining("v2.4.1");
        } finally {
            server.stop(0);
        }
    }
}
