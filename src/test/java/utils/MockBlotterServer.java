package utils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.io.File;
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

    public static void start() {
        // withRootDirectory tells WireMock where to find __files/ and mappings/.
        // Path is relative to the Maven working directory (project root).
        String wireMockRoot = "src/test/resources/wiremock";
        server = new WireMockServer(
                WireMockConfiguration.options()
                        .dynamicPort()
                        .withRootDirectory(wireMockRoot));
        server.start();
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

    private static final String WIREMOCK_ROOT = "src/test/resources/wiremock";

    private static void registerStubs() {
        // ── PT-Blotter SPA: HTML ──────────────────────────────────────────────
        // Serve the pre-built (or Vite-built) index.html at the blotter root.
        // All sub-paths return the same HTML so client-side routing works.
        server.stubFor(get(urlEqualTo("/blotter/"))
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
        File asset = new File(WIREMOCK_ROOT + "/__files/" + bodyFile);
        if (asset.exists()) {
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
                  "trader_id":          "roshkao",
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
