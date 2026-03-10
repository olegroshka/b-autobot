package com.bbot.sandbox.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Lightweight in-memory configuration microservice built on JDK {@code com.sun.net.httpserver}.
 *
 * <h2>Data model</h2>
 * Entries are keyed by {@code "{namespace}/{type}/{key}"} and stored as plain JSON objects
 * (represented as {@code Map<String, Object>}).
 *
 * <h2>REST API</h2>
 * <ul>
 *   <li>{@code GET  /api/config}                   → JSON array of distinct namespaces</li>
 *   <li>{@code GET  /api/config/{ns}}               → JSON array of types under namespace</li>
 *   <li>{@code GET  /api/config/{ns}/{type}}         → JSON object of all keys+values</li>
 *   <li>{@code GET  /api/config/{ns}/{type}/{key}}   → JSON object for a single key</li>
 *   <li>{@code PUT  /api/config/{ns}/{type}/{key}}   → update/create (body = JSON object)</li>
 * </ul>
 *
 * <h2>Static file serving</h2>
 * {@code GET /config-service/**} → served from {@code src/test/resources/config-service-ui/}.
 *
 * <h2>Lifecycle</h2>
 * Started in {@code Hooks.@BeforeAll}, stopped in {@code Hooks.@AfterAll}.
 */
public final class MockConfigServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Maximum notional allowed for a single risk limit entry. */
    private static final long RISK_MAX_NOTIONAL = 50_000_000L;
    /** Fraction of max notional at which a risk alert is triggered. */
    private static final double RISK_ALERT_THRESHOLD = 0.9;

    /** In-memory store: composite key → config object (JSON as Map). */
    private static final ConcurrentHashMap<String, Map<String, Object>> STORE =
            new ConcurrentHashMap<>();

    private static HttpServer server;

    private MockConfigServer() {}

    // ── Seed data ─────────────────────────────────────────────────────────────

    private static void seedData() {
        STORE.clear();
        // Permissions — keyed by real user logins
        put("credit.pt.access", "Permissions", "doej",
                Map.of("isAlgoTrader", false));
        put("credit.pt.access", "Permissions", "smithj",
                Map.of("isAlgoTrader", true));
        put("credit.pt.access", "Permissions", "patelv",
                Map.of("isAlgoTrader", false));
        put("credit.pt.access", "Permissions", "nguyenl",
                Map.of("isAlgoTrader", true));
        // Booking settings
        put("credit.booking", "Settings", "default",
                Map.of("autoBook", false, "bookingDesk", "FIXED_INCOME"));
        // Risk limits
        put("credit.risk", "Limits", "default",
                Map.of("maxNotional", RISK_MAX_NOTIONAL, "alertThreshold", RISK_ALERT_THRESHOLD));
        // Market data sources
        put("market.data", "Sources", "default",
                Map.of("primary", "TW", "fallback", "CP+"));
    }

    private static void put(String ns, String type, String key, Map<String, Object> value) {
        STORE.put(ns + "/" + type + "/" + key, new ConcurrentHashMap<>(value));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Starts on a random dynamic port (used by tests). */
    public static synchronized void start() {
        start(0);
    }

    /** Starts on the given port. Pass {@code 0} for a random dynamic port. */
    public static synchronized void start(int port) {
        if (server != null) return;
        seedData();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/config", MockConfigServer::handleConfig);
            server.createContext("/config-service", MockConfigServer::handleStatic);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start MockConfigServer", e);
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public static String getBaseUrl() {
        assertStarted();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void assertStarted() {
        if (server == null) {
            throw new IllegalStateException(
                    "MockConfigServer has not been started. " +
                    "Ensure Hooks.launchBrowser() calls MockConfigServer.start().");
        }
    }

    // ── REST handler: /api/config ──────────────────────────────────────────────

    private static void handleConfig(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        String method = ex.getRequestMethod();
        String path   = ex.getRequestURI().getPath(); // e.g. /api/config/credit.pt.access/Permissions/trader

        if ("OPTIONS".equals(method)) {
            respond(ex, 204, "");
            return;
        }

        // Strip leading "/api/config" to get the remaining segments
        String[] parts = path.replaceFirst("^/api/config/?", "").split("/", -1);
        // parts[0] = ns or "" if root, parts[1] = type, parts[2] = key

        if ("GET".equals(method)) {
            handleGet(ex, parts);
        } else if ("PUT".equals(method)) {
            handlePut(ex, parts);
        } else if ("DELETE".equals(method)) {
            handleDelete(ex, parts);
        } else {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private static void handleGet(HttpExchange ex, String[] parts) throws IOException {
        if (parts.length == 0 || parts[0].isEmpty()) {
            // GET /api/config → list distinct namespaces
            List<String> namespaces = STORE.keySet().stream()
                    .map(k -> k.split("/")[0])
                    .distinct().sorted().toList();
            respondJson(ex, 200, MAPPER.writeValueAsString(namespaces));

        } else if (parts.length == 1) {
            // GET /api/config/{ns} → list types under namespace
            String ns = parts[0];
            List<String> types = STORE.keySet().stream()
                    .filter(k -> k.startsWith(ns + "/"))
                    .map(k -> k.split("/")[1])
                    .distinct().sorted().toList();
            respondJson(ex, 200, MAPPER.writeValueAsString(types));

        } else if (parts.length == 2) {
            // GET /api/config/{ns}/{type} → all keys+values as JSON object
            String ns = parts[0], type = parts[1];
            String prefix = ns + "/" + type + "/";
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            STORE.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix))
                    .forEach(e -> {
                        String key = e.getKey().substring(prefix.length());
                        result.put(key, e.getValue());
                    });
            respondJson(ex, 200, MAPPER.writeValueAsString(result));

        } else if (parts.length >= 3) {
            // GET /api/config/{ns}/{type}/{key} → single entry
            String storeKey = parts[0] + "/" + parts[1] + "/" + parts[2];
            Map<String, Object> entry = STORE.get(storeKey);
            if (entry == null) {
                respondJson(ex, 404, "{\"error\":\"Not found\"}");
            } else {
                respondJson(ex, 200, MAPPER.writeValueAsString(entry));
            }
        } else {
            respondJson(ex, 400, "{\"error\":\"Bad request\"}");
        }
    }

    @SuppressWarnings("unchecked")
    private static void handlePut(HttpExchange ex, String[] parts) throws IOException {
        if (parts.length < 3) {
            respondJson(ex, 400, "{\"error\":\"PUT requires {ns}/{type}/{key}\"}");
            return;
        }
        String storeKey = parts[0] + "/" + parts[1] + "/" + parts[2];
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> value = MAPPER.readValue(body, Map.class);
        STORE.put(storeKey, new ConcurrentHashMap<>(value));
        respondJson(ex, 200, MAPPER.writeValueAsString(value));
    }

    private static void handleDelete(HttpExchange ex, String[] parts) throws IOException {
        if (parts.length < 3) {
            respondJson(ex, 400, "{\"error\":\"DELETE requires {ns}/{type}/{key}\"}");
            return;
        }
        String storeKey = parts[0] + "/" + parts[1] + "/" + parts[2];
        boolean removed = STORE.remove(storeKey) != null;
        if (removed) {
            respondJson(ex, 200, "{\"deleted\":true}");
        } else {
            respondJson(ex, 404, "{\"error\":\"Not found\"}");
        }
    }

    // ── Static file handler: /config-service ──────────────────────────────────

    private static final String UI_DIR = "src/test/resources/config-service-ui";

    private static void handleStatic(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        if (!"GET".equals(ex.getRequestMethod())) {
            respond(ex, 405, "Method not allowed");
            return;
        }
        String uriPath = ex.getRequestURI().getPath(); // e.g. /config-service/index.html
        String relative = uriPath.replaceFirst("^/config-service/?", "");
        if (relative.isEmpty()) relative = "index.html";

        Path file = Paths.get(UI_DIR, relative);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            // Fallback to index.html for SPA client-side routing
            file = Paths.get(UI_DIR, "index.html");
        }
        if (!Files.exists(file)) {
            respond(ex, 404, "Not found");
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        String ct = contentType(file.getFileName().toString());
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (name.endsWith(".css"))  return "text/css; charset=utf-8";
        if (name.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void respondJson(HttpExchange ex, int status, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json");
        respond(ex, status, body);
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
