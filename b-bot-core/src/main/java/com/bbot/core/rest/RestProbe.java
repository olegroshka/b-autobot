package com.bbot.core.rest;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.registry.BBotRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Lightweight REST client for BDD step definitions.
 *
 * <p>Wraps JDK {@link HttpClient} — no extra dependencies beyond what b-bot-core
 * already ships. Timeout is read from {@code b-bot.timeouts.apiResponse} (default 10 s).
 *
 * <p>All request paths may contain {@code ${key}} tokens that are resolved against
 * {@link ScenarioState} before the request is sent, enabling patterns like:
 * <pre>{@code
 * // After capturing "inquiry_id" from a POST response:
 * GET "/api/inquiry/${inquiry_id}/quote"
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RestProbe probe = RestProbe.of(BBotRegistry.getConfig().getAppApiBase("blotter"));
 * RestResponse resp = probe.post("/api/inquiry", jsonBody);
 * resp.assertStatus(201).assertField("status", "PENDING").capture("inquiry_id");
 * }</pre>
 */
public final class RestProbe {

    private final String     apiBase;
    private final HttpClient client;

    private RestProbe(String apiBase) {
        this.apiBase = apiBase;
        this.client  = HttpClient.newBuilder()
                                 .connectTimeout(resolveTimeout())
                                 .build();
    }

    /** Creates a {@code RestProbe} targeting the given API base URL (no trailing slash). */
    public static RestProbe of(String apiBase) {
        if (apiBase == null || apiBase.isBlank())
            throw new AssertionError("RestProbe: apiBase is null/blank. " +
                "Check that the app is configured in b-bot.apps.{name}.apiBase.");
        return new RestProbe(apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase);
    }

    /**
     * Sends {@code GET {apiBase}{path}} and returns the response.
     * {@code ${key}} tokens in {@code path} are resolved from {@link ScenarioState}.
     */
    public RestResponse get(String path) {
        String resolved = ScenarioState.resolve(path);
        String url = apiBase + resolved;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return new RestResponse(resp.statusCode(), resp.body(), "GET " + url);
        } catch (Exception e) {
            throw new AssertionError("REST GET failed: " + url + " — " + e.getMessage() +
                "\nIs the environment running? scripts/start-mock-uat.sh", e);
        }
    }

    /**
     * Sends {@code POST {apiBase}{path}} with the given JSON body and returns the response.
     * {@code ${key}} tokens in {@code path} are resolved from {@link ScenarioState}.
     */
    public RestResponse post(String path, String jsonBody) {
        String resolved = ScenarioState.resolve(path);
        String url = apiBase + resolved;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return new RestResponse(resp.statusCode(), resp.body(), "POST " + url);
        } catch (Exception e) {
            throw new AssertionError("REST POST failed: " + url + " — " + e.getMessage() +
                "\nIs the environment running? scripts/start-mock-uat.sh", e);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static Duration resolveTimeout() {
        try {
            BBotConfig cfg = BBotRegistry.getConfig();
            if (cfg != null && cfg.hasPath("b-bot.timeouts.apiResponse"))
                return cfg.getTimeout("b-bot.timeouts.apiResponse");
        } catch (Exception ignored) { /* registry not yet initialised */ }
        return Duration.ofSeconds(10);
    }
}
