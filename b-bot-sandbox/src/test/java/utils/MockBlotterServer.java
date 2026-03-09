package com.bbot.sandbox.utils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Embedded WireMock server that replaces {@code https://api.mock-blotter.com}
 * during test execution.
 *
 * <h2>Lifecycle</h2>
 * Started once per JVM in {@code stepdefs.Hooks#launchBrowser()} ({@code @BeforeAll})
 * and stopped in {@code Hooks#shutdownBrowser()} ({@code @AfterAll}).
 *
 * <h2>URL remapping</h2>
 * Call {@link #resolveUrl(String)} in step definitions to transparently swap the
 * production-like hostname for the local WireMock base URL:
 * <pre>{@code
 *   // Gherkin: to 'https://api.mock-blotter.com/submit'
 *   String resolved = MockBlotterServer.resolveUrl(endpoint);
 *   // resolved -> "http://localhost:PORT/submit"
 * }</pre>
 *
 * <h2>Stubs</h2>
 * <ul>
 *   <li>{@code POST /submit} with body containing {@code "UNKNOWN_TRADER"} → 404</li>
 *   <li>{@code POST /submit} (any other body) → 201 with a full portfolio JSON
 *       including a fixed {@link #PORTFOLIO_ID}</li>
 * </ul>
 */
public final class MockBlotterServer {

    /**
     * Portfolio ID that will be returned by every successful mock submission.
     * Generated once per JVM run so each test run has a unique ID.
     * Tests can read this field to know the expected value without parsing HTTP.
     */
    public static final String PORTFOLIO_ID =
            "PF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    private static WireMockServer server;

    private MockBlotterServer() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts on a random dynamic port (default for automated tests).
     * Reads the optional system property {@code blotter.dev.port} — if set,
     * starts on that fixed port instead (used by {@code BlotterDevServer}).
     */
    public static void start() {
        String portProp = System.getProperty("blotter.dev.port");
        start(portProp != null ? Integer.parseInt(portProp) : 0);
    }

    /**
     * Starts on the given port.  Pass {@code 0} for a random dynamic port.
     * Called directly by {@code BlotterDevServer} with a well-known port.
     */
    public static void start(int port) {
        WireMockConfiguration config = WireMockConfiguration.options()
                .usingFilesUnderClasspath("wiremock")
                .extensions(new InquiryStoreTransformer());
        if (port > 0) {
            config = config.port(port);
        } else {
            config = config.dynamicPort();
        }
        server = new WireMockServer(config);
        server.start();
        InquiryStoreTransformer.resetAndSeed(buildSeedInquiries());
        registerStubs();
    }

    public static void stop() {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    public static boolean isRunning() {
        return server != null && server.isRunning();
    }

    // ── URL helpers ───────────────────────────────────────────────────────────

    /** Returns the WireMock base URL, e.g. {@code http://localhost:56789}. */
    public static String getBaseUrl() {
        assertStarted();
        return "http://localhost:" + server.port();
    }

    /**
     * Rewrites the hostname of any {@code api.mock-blotter.com} URL to the
     * local WireMock server.  Other URLs are returned unchanged.
     *
     * @param url URL from Gherkin step (may be the real-looking mock URL)
     * @return The resolved URL pointing at the local WireMock server
     */
    public static String resolveUrl(String url) {
        if (!isRunning()) return url;
        return url.replace("https://api.mock-blotter.com",  getBaseUrl())
                  .replace("http://api.mock-blotter.com",   getBaseUrl());
    }

    // ── Stubs ─────────────────────────────────────────────────────────────────

    /**
     * Returns the URL for the PT-Blotter SPA, e.g. {@code http://localhost:PORT/blotter/}.
     * Requires the WireMock server to be running.
     */
    public static String getBlotterUrl() {
        assertStarted();
        return "http://localhost:" + server.port() + "/blotter/";
    }


    private static void registerStubs() {
        // ── PT-Blotter SPA: HTML ──────────────────────────────────────────────
        // Serve the pre-built (or Vite-built) index.html at the blotter root.
        // All sub-paths return the same HTML so client-side routing works.
        server.stubFor(get(urlPathEqualTo("/blotter/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBodyFile("blotter/index.html")));
        server.stubFor(get(urlEqualTo("/blotter/index.html"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBodyFile("blotter/index.html")));

        // ── PT-Blotter SPA: JS/CSS assets (Vite build output, fixed names) ──
        // Vite is configured with fixed entryFileNames/assetFileNames so the
        // paths are always assets/index.js and assets/index.css regardless of
        // content hash.  Only register stubs when the Vite build has actually run.
        registerAssetStubIfBuilt("blotter/assets/index.js",  "/blotter/assets/.*\\.js",
                "application/javascript; charset=utf-8");
        registerAssetStubIfBuilt("blotter/assets/index.css", "/blotter/assets/.*\\.css",
                "text/css; charset=utf-8");

        // ── PT-Blotter inquiry REST API ────────────────────────────────────────

        // POST /api/inquiry — unknown ISIN → 404 (priority 1 = checked first)
        server.stubFor(post(urlEqualTo("/api/inquiry"))
                .withRequestBody(containing("\"UNKNOWN-ISIN-XYZ\""))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"ISIN not found\"}")));

        // POST /api/inquiry — all other ISINs → 201 (stored in InquiryStoreTransformer)
        server.stubFor(post(urlEqualTo("/api/inquiry"))
                .atPriority(5)
                .willReturn(aResponse()
                        .withTransformers(InquiryStoreTransformer.NAME)
                        .withTransformerParameter("action", "submit")));

        // GET /api/inquiries → 200 from InquiryStoreTransformer (seed + dynamic)
        server.stubFor(get(urlEqualTo("/api/inquiries"))
                .willReturn(aResponse()
                        .withTransformers(InquiryStoreTransformer.NAME)
                        .withTransformerParameter("action", "list")));

        // POST /api/inquiry/{id}/quote → 200 QUOTED
        server.stubFor(post(urlPathMatching("/api/inquiry/.*/quote"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(quoteBody())));

        // POST /api/pt/{ptId}/dealer-cancel → DEALER_REJECT all line items of that PT
        server.stubFor(post(urlPathMatching("/api/pt/.*/dealer-cancel"))
                .willReturn(aResponse()
                        .withTransformers(InquiryStoreTransformer.NAME)
                        .withTransformerParameter("action", "dealer-cancel")));

        // POST /api/pt/{ptId}/customer-cancel → CUSTOMER_REJECT all line items of that PT
        server.stubFor(post(urlPathMatching("/api/pt/.*/customer-cancel"))
                .willReturn(aResponse()
                        .withTransformers(InquiryStoreTransformer.NAME)
                        .withTransformerParameter("action", "customer-cancel")));

        // POST /api/inquiry/{id}/release → 200 RELEASED
        server.stubFor(post(urlPathMatching("/api/inquiry/.*/release"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"RELEASED\"}")));

        // ── 404: unknown trader (higher priority = checked first) ─────────────
        server.stubFor(post(urlEqualTo("/submit"))
                .withRequestBody(containing("\"trader_id\":\"UNKNOWN_TRADER\""))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Trader not found\"}")));

        // ── 201: successful submission ────────────────────────────────────────
        // Values mirror the payload that PortfolioSteps.traderSubmitsPortfolioViaRestApi()
        // sends so that the verify-cell assertions can compare API ↔ grid.
        server.stubFor(post(urlEqualTo("/submit"))
                .atPriority(5)
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody())));
    }

    /** Deterministic inquiry ID used in test assertions (M3). */
    public static final String INQUIRY_ID =
            "INQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    private static String inquiryBody() {
        return String.format(
                "{\"inquiry_id\":\"%s\",\"status\":\"PENDING\"}", INQUIRY_ID);
    }

    private static String quoteBody() {
        return String.format(
                "{\"inquiry_id\":\"%s\",\"status\":\"QUOTED\"," +
                "\"sent_price\":0.0,\"sent_spread\":0.0," +
                "\"timestamp\":\"2026-03-05T00:00:00Z\"}", INQUIRY_ID);
    }

    // Portfolio IDs — mirror seedData.ts constants so API responses are consistent.
    private static final String PT1 = "PT_BBG_20260306_3F7A";
    private static final String PT2 = "PT_BBG_20260306_9C2E";

    /**
     * Builds the list of seed inquiries that pre-populate the store on server start.
     * These mirror the design-contract ISINs from {@code seedData.ts} so the blotter
     * renders them with live-ticking prices.
     */
    private static List<Map<String, Object>> buildSeedInquiries() {
        List<Map<String, Object>> seed = new ArrayList<>();
        seed.add(inquiry("INQ-001", "US912828YJ02", PT1, PT1 + "_1"));
        seed.add(inquiry("INQ-002", "XS2346573523", PT1, PT1 + "_2"));
        seed.add(inquiry("INQ-003", "US38141GXZ20", PT1, PT1 + "_3"));
        seed.add(inquiry("INQ-004", "GB0031348658", PT2, PT2 + "_1"));
        seed.add(inquiry("INQ-005", "FR0014004L86", PT2, PT2 + "_2"));
        return seed;
    }

    private static Map<String, Object> inquiry(String id, String isin, String ptId, String ptLineId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inquiry_id", id);
        m.put("isin",       isin);
        m.put("status",     "PENDING");
        m.put("pt_id",      ptId);
        m.put("pt_line_id", ptLineId);
        return m;
    }

    /**
     * Registers a WireMock stub that serves {@code bodyFile} (relative to
     * {@code __files/}) for any URL matching {@code urlPattern}, but only
     * when the Vite build artifact actually exists on disk.
     *
     * <p>This prevents WireMock from throwing a {@code FileNotFoundException}
     * when the blotter webapp has not been built yet ({@code blotter.build.skip=true}).
     */
    private static void registerAssetStubIfBuilt(
            String bodyFile, String urlPattern, String contentType) {
        boolean exists = MockBlotterServer.class.getClassLoader()
                .getResource("wiremock/__files/" + bodyFile) != null;
        if (exists) {
            server.stubFor(get(urlPathMatching(urlPattern))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", contentType)
                            .withBodyFile(bodyFile)));
        }
    }

    /**
     * Builds the fixed JSON response body.
     *
     * <p>Numeric field values are chosen to exercise the {@code NumericComparator}:
     * {@code total_market_value} is returned as {@code 5937500.0} (one decimal place)
     * while the UI may render it as {@code 5937500} (no decimals), proving that the
     * comparator handles trailing-zero differences.
     */
    private static String successBody() {
        return String.format("""
                {
                  "portfolio_id":       "%s",
                  "trader_id":          "doej",
                  "status":             "SUBMITTED",
                  "currency":           "USD",
                  "desk":               "FIXED_INCOME",
                  "blotter_id":         "BL-FI-001",
                  "total_face_value":   6000000.0,
                  "total_market_value": 5937500.0,
                  "accrued_interest":   12345.67
                }
                """, PORTFOLIO_ID);
    }

    private static void assertStarted() {
        if (server == null || !server.isRunning()) {
            throw new IllegalStateException(
                    "MockBlotterServer has not been started. " +
                    "Ensure Hooks.launchBrowser() calls MockBlotterServer.start().");
        }
    }
}
