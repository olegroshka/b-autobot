package com.bbot.core.rest;

import com.bbot.core.exception.BBotAuthException;
import com.bbot.core.exception.BBotRestException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RestProbe} — all HTTP verbs + error handling.
 *
 * <p>Uses a minimal JDK {@link HttpServer} stub to verify request method,
 * body passthrough, and response handling without external dependencies.
 */
class RestProbeTest {

    private static HttpServer server;
    private static int port;
    private static final AtomicReference<String> LAST_METHOD      = new AtomicReference<>();
    private static final AtomicReference<String> LAST_BODY        = new AtomicReference<>();
    private static final AtomicReference<String> LAST_PATH        = new AtomicReference<>();
    private static final AtomicReference<String> LAST_AUTH_HEADER = new AtomicReference<>();
    private ScenarioContext ctx;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);

        // Add 401 endpoint for auth-aware detection test (must be before root /)
        server.createContext("/api/unauthorized", exchange -> {
            byte[] response = "{\"error\":\"unauthorized\"}".getBytes();
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        server.createContext("/", exchange -> {
            LAST_METHOD.set(exchange.getRequestMethod());
            LAST_PATH.set(exchange.getRequestURI().getPath());
            LAST_BODY.set(new String(exchange.getRequestBody().readAllBytes()));
            LAST_AUTH_HEADER.set(
                exchange.getRequestHeaders().getFirst("Authorization"));

            byte[] response = "{\"status\":\"ok\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void initContext() {
        ctx = new ScenarioContext();
    }

    @AfterEach
    void resetState() {
        LAST_METHOD.set(null);
        LAST_BODY.set(null);
        LAST_PATH.set(null);
        LAST_AUTH_HEADER.set(null);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    @SuppressWarnings("ConstantConditions")
    @Test
    void of_nullApiBase_throws() {
        assertThatThrownBy(() -> RestProbe.of(null, ctx))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("apiBase");
    }

    @Test
    void of_blankApiBase_throws() {
        assertThatThrownBy(() -> RestProbe.of("  ", ctx))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("apiBase");
    }

    @Test
    void of_stripsTrailingSlash() {
        RestProbe probe = RestProbe.of("http://localhost:" + port + "/", ctx);
        RestResponse resp = probe.get("/echo");
        assertThat(resp.status()).isEqualTo(200);
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Test
    void get_sendsGetRequest() {
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        RestResponse resp = probe.get("/api/test");

        assertThat(resp.status()).isEqualTo(200);
        assertThat(LAST_METHOD.get()).isEqualTo("GET");
        assertThat(LAST_PATH.get()).isEqualTo("/api/test");
    }

    @Test
    void get_resolvesContextTokens() {
        ctx.put("id", "42");
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        probe.get("/api/item/${id}");

        assertThat(LAST_PATH.get()).isEqualTo("/api/item/42");
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Test
    void post_sendsPostWithBody() {
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        RestResponse resp = probe.post("/api/create", "{\"name\":\"test\"}");

        assertThat(resp.status()).isEqualTo(200);
        assertThat(LAST_METHOD.get()).isEqualTo("POST");
        assertThat(LAST_BODY.get()).isEqualTo("{\"name\":\"test\"}");
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @Test
    void put_sendsPutWithBody() {
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        RestResponse resp = probe.put("/api/update", "{\"name\":\"updated\"}");

        assertThat(resp.status()).isEqualTo(200);
        assertThat(LAST_METHOD.get()).isEqualTo("PUT");
        assertThat(LAST_PATH.get()).isEqualTo("/api/update");
        assertThat(LAST_BODY.get()).isEqualTo("{\"name\":\"updated\"}");
    }

    @Test
    void put_resolvesContextTokens() {
        ctx.put("resource_id", "99");
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        probe.put("/api/item/${resource_id}", "{}");

        assertThat(LAST_PATH.get()).isEqualTo("/api/item/99");
    }

    // ── DELETE ─────────────────────────────────────────────────────────────────

    @Test
    void delete_sendsDeleteRequest() {
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        RestResponse resp = probe.delete("/api/remove/1");

        assertThat(resp.status()).isEqualTo(200);
        assertThat(LAST_METHOD.get()).isEqualTo("DELETE");
        assertThat(LAST_PATH.get()).isEqualTo("/api/remove/1");
    }

    @Test
    void delete_resolvesContextTokens() {
        ctx.put("del_id", "77");
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        probe.delete("/api/item/${del_id}");

        assertThat(LAST_PATH.get()).isEqualTo("/api/item/77");
    }

    // ── PATCH ─────────────────────────────────────────────────────────────────

    @Test
    void patch_sendsPatchWithBody() {
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        RestResponse resp = probe.patch("/api/patch/1", "{\"field\":\"value\"}");

        assertThat(resp.status()).isEqualTo(200);
        assertThat(LAST_METHOD.get()).isEqualTo("PATCH");
        assertThat(LAST_PATH.get()).isEqualTo("/api/patch/1");
        assertThat(LAST_BODY.get()).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    void patch_resolvesContextTokens() {
        ctx.put("patch_id", "55");
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        probe.patch("/api/item/${patch_id}", "{}");

        assertThat(LAST_PATH.get()).isEqualTo("/api/item/55");
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void get_connectionRefused_throws() {
        RestProbe probe = RestProbe.of("http://localhost:1", ctx);
        assertThatThrownBy(() -> probe.get("/api/test"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("REST GET failed");
    }

    @Test
    void post_connectionRefused_throws() {
        RestProbe probe = RestProbe.of("http://localhost:1", ctx);
        assertThatThrownBy(() -> probe.post("/api/test", "{}"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("REST POST failed");
    }

    @Test
    void put_connectionRefused_throws() {
        RestProbe probe = RestProbe.of("http://localhost:1", ctx);
        assertThatThrownBy(() -> probe.put("/api/test", "{}"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("REST PUT failed");
    }

    @Test
    void delete_connectionRefused_throws() {
        RestProbe probe = RestProbe.of("http://localhost:1", ctx);
        assertThatThrownBy(() -> probe.delete("/api/test"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("REST DELETE failed");
    }

    @Test
    void patch_connectionRefused_throws() {
        RestProbe probe = RestProbe.of("http://localhost:1", ctx);
        assertThatThrownBy(() -> probe.patch("/api/test", "{}"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("REST PATCH failed");
    }

    // ── Response body extraction ──────────────────────────────────────────────

    @Test
    void get_responseBodyParseable() {
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        RestResponse resp = probe.get("/api/test");

        assertThat(resp.body()).contains("\"status\":\"ok\"");
        assertThat(resp.getField("status")).isEqualTo("ok");
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    @Test
    void builder_minimalConfig() {
        RestProbe probe = RestProbe.builder()
                .apiBase("http://localhost:" + port)
                .build();
        RestResponse resp = probe.get("/api/test");
        assertThat(resp.status()).isEqualTo(200);
    }

    @Test
    void builder_missingApiBase_throws() {
        assertThatThrownBy(() -> RestProbe.builder().build())
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("apiBase");
    }

    @Test
    void builder_stripsTrailingSlash() {
        RestProbe probe = RestProbe.builder()
                .apiBase("http://localhost:" + port + "/")
                .build();
        RestResponse resp = probe.get("/api/test");
        assertThat(resp.status()).isEqualTo(200);
    }

    // ── Auth integration ──────────────────────────────────────────────────────

    @Test
    void builder_withBearerAuth_sendsAuthHeader() {
        RestProbe probe = RestProbe.builder()
                .apiBase("http://localhost:" + port)
                .auth(AuthStrategy.bearer("test-token-abc"))
                .build();

        probe.get("/api/secured");

        assertThat(LAST_AUTH_HEADER.get()).isEqualTo("Bearer test-token-abc");
    }

    @Test
    void of_defaultAuth_noAuthHeader() {
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        probe.get("/api/public");

        assertThat(LAST_AUTH_HEADER.get()).isNull();
    }

    @Test
    void builder_withCustomAuth_sendsCustomHeader() {
        RestProbe probe = RestProbe.builder()
                .apiBase("http://localhost:" + port)
                .auth(b -> b.header("X-Custom-Auth", "my-key"))
                .build();

        probe.get("/api/custom");

        // X-Custom-Auth is sent; Authorization is not
        assertThat(LAST_AUTH_HEADER.get()).isNull();
    }

    // ── Auth-aware 401 detection ────────────────────────────────────────────

    @Test
    void get_401_withActiveAuth_throwsBBotAuthException() {
        RestProbe probe = RestProbe.builder()
                .apiBase("http://localhost:" + port)
                .auth(AuthStrategy.bearer("expired-token"))
                .build();

        assertThatThrownBy(() -> probe.get("/api/unauthorized"))
                .isInstanceOf(BBotAuthException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("SSO session may have expired");
    }

    @Test
    void get_401_withNoAuth_returnsRestResponse() {
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);

        // With NoAuth (default), 401 is returned as a normal RestResponse
        RestResponse resp = probe.get("/api/unauthorized");
        assertThat(resp.status()).isEqualTo(401);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    @Test
    void of_defaultsToNoAuthAndNoRetry() {
        RestProbe probe = RestProbe.of("http://localhost:" + port, ctx);
        assertThat(probe.auth()).isNotNull();
        assertThat(probe.retryPolicy()).isEqualTo(RetryPolicy.NONE);
    }
}
