package com.bbot.core.rest;

import com.bbot.core.exception.BBotRestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Lightweight REST client for BDD step definitions.
 *
 * <p>Wraps JDK {@link HttpClient} — no extra dependencies beyond what b-bot-core
 * already ships. Supports GET, POST, PUT, DELETE, and PATCH with optional
 * authentication ({@link AuthStrategy}) and retry ({@link RetryPolicy}).
 *
 * <p>All request paths may contain {@code ${key}} tokens that are resolved against
 * {@link ScenarioState} before the request is sent.
 *
 * <h2>Quick usage</h2>
 * <pre>{@code
 * RestProbe probe = RestProbe.of(apiBase);
 * probe.post("/api/inquiry", body).assertStatus(201).capture("inquiry_id");
 * }</pre>
 *
 * <h2>Builder usage (auth + retry)</h2>
 * <pre>{@code
 * RestProbe probe = RestProbe.builder()
 *     .apiBase(apiBase)
 *     .auth(AuthStrategy.bearer(token))
 *     .retryPolicy(RetryPolicy.serverErrors(3, 500))
 *     .build();
 * }</pre>
 */
public final class RestProbe implements RestClient {

    private static final Logger LOG = LoggerFactory.getLogger(RestProbe.class);

    private final String       apiBase;
    private final HttpClient   client;
    private final AuthStrategy auth;
    private final RetryPolicy  retryPolicy;

    private RestProbe(String apiBase, HttpClient client, AuthStrategy auth, RetryPolicy retryPolicy) {
        this.apiBase     = apiBase;
        this.client      = client;
        this.auth        = auth;
        this.retryPolicy = retryPolicy;
    }

    /** Creates a {@code RestProbe} with default settings (no auth, no retry). */
    public static RestProbe of(String apiBase) {
        if (apiBase == null || apiBase.isBlank())
            throw new BBotRestException("RestProbe: apiBase is null/blank. " +
                "Check that the app is configured in b-bot.apps.{name}.apiBase.",
                "", "", 0, "");
        String base = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        return new RestProbe(base, HttpClientFactory.shared(), AuthStrategy.none(), RetryPolicy.NONE);
    }

    /** Returns a builder for creating a fully-configured {@code RestProbe}. */
    public static Builder builder() { return new Builder(); }

    /** Returns the configured {@link AuthStrategy}. */
    public AuthStrategy auth() { return auth; }

    /** Returns the configured {@link RetryPolicy}. */
    public RetryPolicy retryPolicy() { return retryPolicy; }

    // ── RestClient implementation ─────────────────────────────────────────────

    @Override public RestResponse get(String path)                    { return execute("GET",    path, null);     }
    @Override public RestResponse post(String path, String jsonBody)  { return execute("POST",   path, jsonBody); }
    @Override public RestResponse put(String path, String jsonBody)   { return execute("PUT",    path, jsonBody); }
    @Override public RestResponse delete(String path)                 { return execute("DELETE", path, null);     }
    @Override public RestResponse patch(String path, String jsonBody) { return execute("PATCH",  path, jsonBody); }

    // ── Internal ──────────────────────────────────────────────────────────────

    private RestResponse execute(String method, String path, String jsonBody) {
        String resolved = ScenarioState.resolve(path);
        String url = apiBase + resolved;
        LOG.debug("REST {} {}", method, url);
        try {
            HttpRequest req = buildRequest(method, url, jsonBody);
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            // Retry loop
            int attempt = 0;
            while (attempt < retryPolicy.maxRetries() && retryPolicy.shouldRetry(resp.statusCode())) {
                attempt++;
                long delay = retryPolicy.delayMs(attempt);
                LOG.debug("REST {} {} → HTTP {} — retrying ({}/{}) after {}ms",
                          method, url, resp.statusCode(), attempt, retryPolicy.maxRetries(), delay);
                Thread.sleep(delay);
                resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            }

            LOG.debug("REST {} {} → HTTP {}", method, url, resp.statusCode());
            return new RestResponse(resp.statusCode(), resp.body(), method + " " + url);
        } catch (BBotRestException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BBotRestException("REST " + method + " interrupted: " + url,
                    method, url, e);
        } catch (Exception e) {
            throw new BBotRestException("REST " + method + " failed: " + url + " — " + e.getMessage() +
                "\nIs the environment running? scripts/start-mock-uat.sh",
                method, url, e);
        }
    }

    private HttpRequest buildRequest(String method, String url, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        auth.apply(builder);
        switch (method) {
            case "GET"    -> builder.GET();
            case "DELETE" -> builder.DELETE();
            case "POST"   -> builder.header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            case "PUT"    -> builder.header("Content-Type", "application/json")
                                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
            case "PATCH"  -> builder.header("Content-Type", "application/json")
                                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody));
            default       -> builder.method(method,
                    jsonBody != null ? HttpRequest.BodyPublishers.ofString(jsonBody)
                                    : HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /** Fluent builder for {@link RestProbe}. */
    public static final class Builder {
        private String       apiBase;
        private HttpClient   client;
        private AuthStrategy auth        = AuthStrategy.none();
        private RetryPolicy  retryPolicy = RetryPolicy.NONE;

        Builder() {}

        public Builder apiBase(String apiBase)           { this.apiBase     = apiBase;     return this; }
        public Builder client(HttpClient client)         { this.client      = client;      return this; }
        public Builder auth(AuthStrategy auth)           { this.auth        = auth;        return this; }
        public Builder retryPolicy(RetryPolicy policy)   { this.retryPolicy = policy;      return this; }

        public RestProbe build() {
            if (apiBase == null || apiBase.isBlank())
                throw new BBotRestException("RestProbe.Builder: apiBase is required.",
                        "", "", 0, "");
            String base = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
            HttpClient c = client != null ? client : HttpClientFactory.shared();
            return new RestProbe(base, c, auth, retryPolicy);
        }
    }
}
