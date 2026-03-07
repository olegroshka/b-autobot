package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import com.microsoft.playwright.Page;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all {@link AppDescriptor}s.
 *
 * <h2>Lifecycle — all calls from the consuming module's {@code Hooks.java}</h2>
 * <ol>
 *   <li>{@link #register(AppDescriptor)}  — once per app, during {@code @BeforeAll}</li>
 *   <li>{@link #initialize(BBotConfig)}   — once after all servers started; resolves {@link AppContext}s</li>
 *   <li>{@link #dsl(String, Page, Class)} — per scenario; creates a fresh DSL via {@link DslFactory}</li>
 *   <li>{@link #checkHealth(String)}      — per precondition; asserts liveness via health endpoint</li>
 *   <li>{@link #assertVersion(String, String)} — per precondition; asserts deployed version</li>
 *   <li>{@link #reset()}                  — in {@code @AfterAll} to clear state for the next JVM run</li>
 * </ol>
 */
public final class BBotRegistry {

    private static final Map<String, AppDescriptor<?>> DESCRIPTORS = new LinkedHashMap<>();
    private static final Map<String, AppContext>        CONTEXTS    = new ConcurrentHashMap<>();

    private BBotRegistry() {}

    // ── Registration ─────────────────────────────────────────────────────────

    /** Registers a descriptor. Call once per app in {@code @BeforeAll}, before {@link #initialize}. */
    public static void register(AppDescriptor<?> descriptor) {
        DESCRIPTORS.put(descriptor.name(), descriptor);
    }

    /**
     * Resolves an {@link AppContext} for every registered descriptor from the supplied config.
     * Call once in {@code @BeforeAll} after all dynamic-port servers have started and
     * any runtime URL overrides have been applied via {@link BBotConfig#withOverrides}.
     */
    public static void initialize(BBotConfig cfg) {
        DESCRIPTORS.forEach((name, desc) ->
            CONTEXTS.put(name, AppContext.fromConfig(name, cfg)));
    }

    // ── DSL factory ───────────────────────────────────────────────────────────

    /**
     * Returns a fresh DSL for the named app, bound to the current scenario's page.
     * Invokes the descriptor's {@link DslFactory} — one fresh instance per scenario.
     *
     * @param appName  registered name, e.g. {@code "blotter"}
     * @param page     current scenario's Playwright page; {@code null} for REST-only apps
     * @param dslType  DSL class for type-safe cast (prevents accidental miscast)
     */
    @SuppressWarnings("unchecked")
    public static <D> D dsl(String appName, Page page, Class<D> dslType) {
        AppDescriptor<D> desc = (AppDescriptor<D>) requireDescriptor(appName);
        AppContext ctx = requireContext(appName);
        return desc.dslFactory().create(ctx, page);
    }

    // ── Health / version assertions ───────────────────────────────────────────

    /**
     * Asserts the named app's health endpoint returns 2xx.
     * No-op if the descriptor declares no {@link AppDescriptor#healthCheckPath}.
     */
    public static void checkHealth(String appName) {
        AppDescriptor<?> desc = requireDescriptor(appName);
        AppContext ctx = requireContext(appName);
        desc.healthCheckPath().ifPresent(path -> {
            String url = ctx.getApiBaseUrl() + path;
            int status = httpGetStatus(url);
            if (status < 200 || status >= 300) {
                throw new AssertionError(
                    "Health check FAILED for '" + appName + "': " +
                    "GET " + url + " returned HTTP " + status);
            }
        });
    }

    /**
     * Asserts the named app's version endpoint returns JSON containing the expected version string.
     * No-op if the descriptor declares no {@link AppDescriptor#versionPath}.
     */
    public static void assertVersion(String appName, String expectedVersion) {
        AppDescriptor<?> desc = requireDescriptor(appName);
        AppContext ctx = requireContext(appName);
        desc.versionPath().ifPresent(path -> {
            String body = httpGetBody(ctx.getApiBaseUrl() + path);
            if (!body.contains("\"" + expectedVersion + "\"")) {
                throw new AssertionError(
                    "Version mismatch for '" + appName + "': expected \"" +
                    expectedVersion + "\" but response was: " + body);
            }
        });
    }

    /** Checks health of all registered apps that declare a {@link AppDescriptor#healthCheckPath}. */
    public static void checkAllHealth() {
        DESCRIPTORS.keySet().forEach(BBotRegistry::checkHealth);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Clears all state. Call in {@code @AfterAll} so the JVM can be reused cleanly. */
    public static void reset() {
        DESCRIPTORS.clear();
        CONTEXTS.clear();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static AppDescriptor<?> requireDescriptor(String name) {
        AppDescriptor<?> d = DESCRIPTORS.get(name);
        if (d == null) throw new IllegalArgumentException(
            "No AppDescriptor registered for '" + name + "'. " +
            "Registered names: " + DESCRIPTORS.keySet());
        return d;
    }

    private static AppContext requireContext(String name) {
        AppContext ctx = CONTEXTS.get(name);
        if (ctx == null) throw new IllegalStateException(
            "BBotRegistry not initialised for '" + name + "'. " +
            "Call BBotRegistry.initialize(config) in @BeforeAll " +
            "after all servers are started.");
        return ctx;
    }

    private static int httpGetStatus(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            throw new AssertionError("Health check HTTP request failed for: " + url, e);
        }
    }

    private static String httpGetBody(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            return client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            throw new AssertionError("Version check HTTP request failed for: " + url, e);
        }
    }
}
