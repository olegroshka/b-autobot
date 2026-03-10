package com.bbot.template.utils;

import com.bbot.core.registry.AppContext;
import com.bbot.core.rest.HttpClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * REST DSL for the Config Service microservice.
 *
 * <p>Uses the shared JDK {@link HttpClient} from {@link HttpClientFactory} —
 * no Playwright, no browser.
 * All methods throw {@link AssertionError} on unexpected responses so that
 * Cucumber marks the step as FAILED with a clear message.
 *
 * <p>The base URL is injected at construction time from the active environment
 * config ({@code b-bot.apps.config-service.apiBase}) — no hardcoded hosts.
 */
public final class ConfigServiceDsl {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient   CLIENT = HttpClientFactory.shared();

    private final String apiBase;
    private final String configRoot; // resolved from api-actions.list-config.path

    public ConfigServiceDsl(AppContext ctx) {
        this.apiBase    = ctx.getApiBaseUrl();
        this.configRoot = ctx.getActionPath("list-config");
    }

    // ── Namespace ─────────────────────────────────────────────────────────────

    /**
     * Asserts that the given namespace is registered in the config service.
     *
     * <p>GET /api/config returns a JSON array of namespace strings.
     * Passes if {@code namespace} appears in that array.
     */
    public void assertNamespacePresent(String namespace) {
        String body = get(configRoot);
        try {
            JsonNode root = MAPPER.readTree(body);
            if (!root.isArray())
                throw new AssertionError("GET /api/config did not return a JSON array: " + body);
            for (JsonNode node : root) {
                if (namespace.equals(node.asText())) return;
            }
            throw new AssertionError(
                    "Namespace '" + namespace + "' not found in config service. Got: " + body);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse /api/config response: " + body, e);
        }
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    /**
     * Returns the {@code isAlgoTrader} flag for the given username.
     *
     * <p>GET /api/config/credit.pt.access/Permissions/{username} returns a JSON object.
     */
    public boolean getUserIsAlgoTrader(String username) {
        String path = configRoot + "/credit.pt.access/Permissions/" + username;
        String body = get(path);
        try {
            JsonNode node = MAPPER.readTree(body);
            if (!node.has("isAlgoTrader"))
                throw new AssertionError(
                        "Response for user '" + username + "' has no isAlgoTrader field: " + body);
            return node.get("isAlgoTrader").asBoolean();
        } catch (Exception e) {
            throw new AssertionError("Failed to parse response for user '" + username + "': " + body, e);
        }
    }

    /**
     * Asserts that the user's {@code isAlgoTrader} flag matches {@code expected}.
     */
    public void assertUserIsAlgoTrader(String username, boolean expected) {
        boolean actual = getUserIsAlgoTrader(username);
        if (actual != expected)
            throw new AssertionError(
                    "isAlgoTrader for '" + username + "': expected " + expected + " but was " + actual);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String get(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + path))
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400)
                throw new AssertionError(
                        "GET " + path + " returned HTTP " + resp.statusCode() +
                        ". Is the Config Service running at " + apiBase + "?");
            return resp.body();
        } catch (Exception e) {
            throw new AssertionError(
                    "HTTP request failed: GET " + apiBase + path +
                    "\nIs the mock UAT environment running? " +
                    "Start it with: scripts/start-mock-uat.sh  (or .bat on Windows)", e);
        }
    }
}
