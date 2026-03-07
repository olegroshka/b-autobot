package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BBotRegistryTest {

    @AfterEach
    void cleanup() {
        BBotRegistry.reset();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AppDescriptor<String> descriptor(String name) {
        return descriptor(name, Optional.empty(), Optional.empty());
    }

    private static AppDescriptor<String> descriptor(
            String name, Optional<String> healthPath, Optional<String> versionPath) {
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

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void registerAndDslCallsFactory() {
        BBotRegistry.register(descriptor("myapp"));
        BBotRegistry.initialize(BBotConfig.load());

        String result = BBotRegistry.dsl("myapp", null, String.class);
        assertThat(result).isEqualTo("dsl-for-myapp");
    }

    @Test
    void unknownAppNameThrows() {
        BBotRegistry.register(descriptor("known"));
        BBotRegistry.initialize(BBotConfig.load());

        assertThatThrownBy(() -> BBotRegistry.dsl("nonexistent", null, String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nonexistent")
            .hasMessageContaining("known");
    }

    @Test
    void dslBeforeInitializeThrows() {
        BBotRegistry.register(descriptor("myapp"));
        // initialize() NOT called

        assertThatThrownBy(() -> BBotRegistry.dsl("myapp", null, String.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("initialize");
    }

    @Test
    void resetClearsAll() {
        BBotRegistry.register(descriptor("myapp"));
        BBotRegistry.initialize(BBotConfig.load());

        BBotRegistry.reset();

        // After reset, both descriptor and context are gone
        assertThatThrownBy(() -> BBotRegistry.dsl("myapp", null, String.class))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkHealthNoOpWhenNoPath() {
        // Descriptor with no healthCheckPath — should not make any HTTP call
        // (if it did it would fail because there is no server running)
        BBotRegistry.register(descriptor("no-health"));
        BBotRegistry.initialize(configWithApp("no-health", "http://localhost:1"));

        // Must complete without exception
        BBotRegistry.checkHealth("no-health");
    }

    @Test
    void checkHealthCallsEndpointAndSucceedsOn2xx() throws IOException {
        // Start a minimal JDK HTTP server that returns 200
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
            BBotRegistry.register(descriptor("svc", Optional.of("/health"), Optional.empty()));
            BBotRegistry.initialize(configWithApp("svc", "http://localhost:" + port));

            BBotRegistry.checkHealth("svc");

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
            BBotRegistry.register(descriptor("failing-svc", Optional.of("/health"), Optional.empty()));
            BBotRegistry.initialize(configWithApp("failing-svc", "http://localhost:" + port));

            assertThatThrownBy(() -> BBotRegistry.checkHealth("failing-svc"))
                .isInstanceOf(AssertionError.class)
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
            BBotRegistry.register(descriptor("versioned-svc", Optional.empty(), Optional.of("/version")));
            BBotRegistry.initialize(configWithApp("versioned-svc", "http://localhost:" + port));

            // Must not throw
            BBotRegistry.assertVersion("versioned-svc", "v2.4.1");
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
            BBotRegistry.register(descriptor("versioned-svc", Optional.empty(), Optional.of("/version")));
            BBotRegistry.initialize(configWithApp("versioned-svc", "http://localhost:" + port));

            assertThatThrownBy(() -> BBotRegistry.assertVersion("versioned-svc", "v2.4.1"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("v2.4.1");
        } finally {
            server.stop(0);
        }
    }
}
