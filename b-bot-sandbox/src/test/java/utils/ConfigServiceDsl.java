package com.bbot.sandbox.utils;

import com.bbot.core.rest.HttpClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DSL for the Config Service REST API.
 *
 * <p>Uses the shared JDK {@link HttpClient} from {@link HttpClientFactory} — zero
 * new test-scope dependencies. The base URL is injected at construction time via
 * {@link com.bbot.core.registry.AppContext#getApiBaseUrl()}.
 */
public final class ConfigServiceDsl {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient   CLIENT = HttpClientFactory.shared();

    private final String baseUrl;

    public ConfigServiceDsl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    /** Asserts that GET /api/config returns an array containing the given namespace. */
    public void assertNamespacePresent(String ns) throws IOException, InterruptedException {
        String body = get("/api/config");
        JsonNode arr = MAPPER.readTree(body);
        assertThat(arr.isArray()).as("GET /api/config should return a JSON array").isTrue();
        boolean found = false;
        for (JsonNode n : arr) { if (ns.equals(n.asText())) { found = true; break; } }
        assertThat(found).as("Namespace '%s' should be in the list", ns).isTrue();
    }

    // ── Config read / write ───────────────────────────────────────────────────

    /** GET /api/config/{ns}/{type}/{key} → JsonNode. */
    public JsonNode readConfig(String ns, String type, String key)
            throws IOException, InterruptedException {
        String body = get("/api/config/" + ns + "/" + type + "/" + key);
        return MAPPER.readTree(body);
    }

    /**
     * GET /api/config/{ns}/{type}/{key}, extract field and return as String.
     * Booleans are returned as "true"/"false" (lowercased).
     */
    public String getFieldValue(String ns, String type, String key, String field)
            throws IOException, InterruptedException {
        JsonNode node = readConfig(ns, type, key);
        JsonNode fieldNode = node.get(field);
        assertThat(fieldNode).as("Field '%s' should exist in config", field).isNotNull();
        return fieldNode.asText();
    }

    /**
     * PUT /api/config/{ns}/{type}/{key} — update a single field in the entry.
     * The server stores the complete body so we read the current value first,
     * merge the changed field, then PUT the whole object.
     */
    public void updateConfig(String ns, String type, String key, String field, Object value)
            throws IOException, InterruptedException {
        // Read current entry (may not exist yet — treat 404 as empty object)
        String path0 = "/api/config/" + ns + "/" + type + "/" + key;
        HttpRequest r0 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path0)).GET().build();
        HttpResponse<String> r0resp = CLIENT.send(r0, HttpResponse.BodyHandlers.ofString());
        ObjectNode node = r0resp.statusCode() == 200
                ? (ObjectNode) MAPPER.readTree(r0resp.body())
                : MAPPER.createObjectNode();

        // Set the field to the new value (coerce string "true"/"false" to boolean)
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s))       node.put(field, true);
            else if ("false".equalsIgnoreCase(s)) node.put(field, false);
            else {
                // Try numeric
                try { node.put(field, Double.parseDouble(s)); }
                catch (NumberFormatException e) { node.put(field, s); }
            }
        } else if (value instanceof Boolean b) {
            node.put(field, b);
        } else if (value instanceof Number n) {
            node.put(field, n.doubleValue());
        }

        // PUT the merged object
        String path = "/api/config/" + ns + "/" + type + "/" + key;
        String body = MAPPER.writeValueAsString(node);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).as("PUT config should return 200").isEqualTo(200);
    }

    /** GET /api/config/{ns} — asserts returned array contains the given type. */
    public void assertTypePresent(String ns, String type) throws IOException, InterruptedException {
        String body = get("/api/config/" + ns);
        JsonNode arr = MAPPER.readTree(body);
        assertThat(arr.isArray()).as("GET /api/config/%s should return a JSON array", ns).isTrue();
        boolean found = false;
        for (JsonNode n : arr) { if (type.equals(n.asText())) { found = true; break; } }
        assertThat(found).as("Type '%s' should be listed under namespace '%s'", type, ns).isTrue();
    }

    /** GET /api/config/{ns}/{type} — asserts returned object contains the given key. */
    public void assertKeyPresent(String ns, String type, String key) throws IOException, InterruptedException {
        String body = get("/api/config/" + ns + "/" + type);
        JsonNode obj = MAPPER.readTree(body);
        assertThat(obj.has(key))
                .as("Key '%s' should be present in %s/%s", key, ns, type)
                .isTrue();
    }

    /** DELETE /api/config/{ns}/{type}/{key} — asserts 200 + deleted:true. */
    public void deleteConfig(String ns, String type, String key) throws IOException, InterruptedException {
        String path = "/api/config/" + ns + "/" + type + "/" + key;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .DELETE().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).as("DELETE config should return 200").isEqualTo(200);
        JsonNode body = MAPPER.readTree(resp.body());
        assertThat(body.path("deleted").asBoolean())
                .as("Response should have deleted:true").isTrue();
    }

    /** GET /api/config/{ns}/{type}/{key} — asserts the server returns 404. */
    public void assertEntryNotFound(String ns, String type, String key)
            throws IOException, InterruptedException {
        String path = "/api/config/" + ns + "/" + type + "/" + key;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode())
                .as("GET %s should return 404 (entry absent)", path)
                .isEqualTo(404);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String get(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode())
                .as("GET %s should return 200 (got %d): %s", path, resp.statusCode(), resp.body())
                .isEqualTo(200);
        return resp.body();
    }
}
