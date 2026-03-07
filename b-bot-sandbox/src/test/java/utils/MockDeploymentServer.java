package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Lightweight in-memory deployment registry built on JDK {@code com.sun.net.httpserver}.
 *
 * <h2>Seed data</h2>
 * 12 UAT services representing a typical fixed-income trading platform stack:
 * three credit trading services (the SUT), risk, market data, booking, PnL,
 * treasury, regulatory reporting and monitoring.  Two services are intentionally
 * unhealthy to make the dashboard realistic.
 *
 * <h2>REST API (read-only)</h2>
 * <ul>
 *   <li>{@code GET  /api/deployments}        → JSON array of all deployment records</li>
 *   <li>{@code GET  /api/deployments/{name}} → JSON object for one service</li>
 * </ul>
 *
 * <h2>Static file serving</h2>
 * {@code GET /deployment/**} → served from {@code src/test/resources/deployment-ui/}.
 *
 * <h2>Lifecycle</h2>
 * Started in {@code Hooks.@BeforeAll}, stopped in {@code Hooks.@AfterAll}.
 */
public final class MockDeploymentServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Store keyed by service name.  {@link LinkedHashMap} preserves insertion
     * order so the GET-all response is stable across runs.
     */
    private static final Map<String, Map<String, Object>> STORE =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private static HttpServer server;

    private MockDeploymentServer() {}

    // ── Seed data ─────────────────────────────────────────────────────────────

    private static void seedData() {
        STORE.clear();

        // ── Credit Trading — Systems Under Test ─────────────────────────────
        put("credit-rfq-blotter",    "RUNNING", "v2.4.1", "UAT", "uat-app-01",  8080, "Credit Trading Tech",  "2026-03-05T14:30:00Z", "build-4821", "5d 2h");
        put("credit-pt-pricer",      "RUNNING", "v1.8.3", "UAT", "uat-app-02",  8082, "Credit Trading Tech",  "2026-03-04T09:15:00Z", "build-3907", "6d 17h");
        put("credit-pt-neg-engine",  "RUNNING", "v3.1.0", "UAT", "uat-app-03",  8083, "Credit Trading Tech",  "2026-03-06T11:45:00Z", "build-5210", "19h 15m");

        // ── Supporting infrastructure ────────────────────────────────────────
        put("credit-risk-engine",    "RUNNING", "v2.0.5", "UAT", "uat-risk-01", 9090, "Risk Technology",      "2026-03-01T08:00:00Z", "build-2341", "6d 8h");
        put("market-data-gateway",   "RUNNING", "v4.2.1", "UAT", "uat-mkt-01",  7070, "Market Data",          "2026-02-28T16:20:00Z", "build-6102", "7d 0h");
        put("trade-booking-service", "RUNNING", "v1.5.2", "UAT", "uat-book-01", 8090, "Operations Tech",      "2026-03-03T12:00:00Z", "build-1893", "4d 4h");
        put("position-aggregator",   "RUNNING", "v2.3.4", "UAT", "uat-risk-02", 9091, "Risk Technology",      "2026-03-02T10:30:00Z", "build-2987", "4d 18h");
        put("rate-curve-builder",    "RUNNING", "v3.0.1", "UAT", "uat-mkt-02",  7071, "Market Data",          "2026-03-05T08:00:00Z", "build-6050", "5d 8h");
        put("collateral-manager",    "RUNNING", "v2.8.0", "UAT", "uat-coll-01", 9092, "Treasury Tech",        "2026-02-27T14:00:00Z", "build-4401", "8d 2h");
        put("p-and-l-calculator",    "RUNNING", "v2.1.7", "UAT", "uat-pnl-01",  9093, "Finance Tech",         "2026-03-04T15:45:00Z", "build-3512", "2d 11h");

        // ── Unhealthy — realistic UAT noise ─────────────────────────────────
        put("regulatory-reporting",  "STOPPED", "v1.1.0", "UAT", "uat-reg-01",  9094, "Compliance Tech",      "2026-02-20T09:00:00Z", "build-1201", "-");
        put("limit-monitor",         "FAILED",  "v1.9.2", "UAT", "uat-risk-03", 9095, "Risk Technology",      "2026-03-06T06:00:00Z", "build-3001", "-");
    }

    private static void put(String name, String status, String version, String env,
                            String host, int port, String team,
                            String lastDeployed, String build, String uptime) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("name",         name);
        rec.put("status",       status);
        rec.put("version",      version);
        rec.put("environment",  env);
        rec.put("host",         host);
        rec.put("port",         port);
        rec.put("team",         team);
        rec.put("lastDeployed", lastDeployed);
        rec.put("build",        build);
        rec.put("uptime",       uptime);
        STORE.put(name, rec);
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
            server.createContext("/api/deployments", MockDeploymentServer::handleApi);
            server.createContext("/deployment",      MockDeploymentServer::handleStatic);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start MockDeploymentServer", e);
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public static String getBaseUrl() {
        if (server == null) {
            throw new IllegalStateException(
                    "MockDeploymentServer has not been started. " +
                    "Ensure Hooks.launchBrowser() calls MockDeploymentServer.start().");
        }
        return "http://localhost:" + server.getAddress().getPort();
    }

    // ── REST handler: /api/deployments ────────────────────────────────────────

    private static void handleApi(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        if (!"GET".equals(ex.getRequestMethod()))    { respondJson(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }

        String path = ex.getRequestURI().getPath();
        // /api/deployments or /api/deployments/{name}
        String name = path.replaceFirst("^/api/deployments/?", "");

        if (name.isEmpty()) {
            // Return all services sorted by name
            List<Map<String, Object>> all = new ArrayList<>(STORE.values());
            all.sort(Comparator.comparing(m -> (String) m.get("name")));
            respondJson(ex, 200, MAPPER.writeValueAsString(all));
        } else {
            Map<String, Object> rec = STORE.get(name);
            if (rec == null) respondJson(ex, 404, "{\"error\":\"Service not found\"}");
            else             respondJson(ex, 200, MAPPER.writeValueAsString(rec));
        }
    }

    // ── Static file handler: /deployment ─────────────────────────────────────

    private static final String UI_DIR = "src/test/resources/deployment-ui";

    private static void handleStatic(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        if (!"GET".equals(ex.getRequestMethod())) { respond(ex, 405, "Method not allowed"); return; }

        String uriPath = ex.getRequestURI().getPath();
        String relative = uriPath.replaceFirst("^/deployment/?", "");
        if (relative.isEmpty()) relative = "index.html";

        Path file = Paths.get(UI_DIR, relative);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            file = Paths.get(UI_DIR, "index.html"); // SPA fallback
        }
        if (!Files.exists(file)) { respond(ex, 404, "Not found"); return; }

        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
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
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void respondJson(HttpExchange ex, int status, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json");
        respond(ex, status, body);
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
