package com.bbot.core.registry;

import com.bbot.core.config.BBotConfig;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * <p>Since M11, every static method delegates to a {@link BBotSession} instance.
 * New code should prefer the instance API via {@link #session()}.
 */
public final class BBotRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(BBotRegistry.class);

    /** The accumulated builder between register() and initialize(). */
    private static volatile BBotSession.Builder BUILDER = null;
    /** The immutable session produced by initialize(). */
    private static volatile BBotSession INSTANCE = null;
    /** Config reference kept for getConfig() before / after session lifecycle. */
    private static volatile BBotConfig CURRENT_CONFIG = null;

    private BBotRegistry() {}

    // ── Registration ─────────────────────────────────────────────────────────

    /** @deprecated Use {@link BBotSession.Builder#register(AppDescriptor)} instead. */
    @Deprecated(since = "1.1", forRemoval = true)
    public static void register(AppDescriptor<?> descriptor) {
        if (BUILDER == null) {
            BUILDER = BBotSession.builder();
        }
        BUILDER.register(descriptor);
        LOG.info("Registered app descriptor '{}'", descriptor.name());
    }

    /**
     * Resolves an {@link AppContext} for every registered descriptor from the supplied config.
     * Call once in {@code @BeforeAll} after all dynamic-port servers have started and
     * any runtime URL overrides have been applied via {@link BBotConfig#withOverrides}.
     *
     * @deprecated Use {@link BBotSession.Builder#initialize(BBotConfig)} and {@link BBotSession.Builder#build()} instead.
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public static void initialize(BBotConfig cfg) {
        if (BUILDER == null) {
            BUILDER = BBotSession.builder();
        }
        INSTANCE = BUILDER.initialize(cfg).build();
        CURRENT_CONFIG = cfg;
        BUILDER = null; // consumed — prevent double build
        LOG.info("Registry initialised — {} app(s): {}", INSTANCE.appNames().size(), INSTANCE.appNames());
    }

    /**
     * Returns the {@link BBotConfig} supplied to {@link #initialize(BBotConfig)},
     * or {@code null} if the registry has not yet been initialised.
     *
     * <p>Core utilities ({@code PlaywrightManager}, {@code GridHarness},
     * {@code TickingCellHelper}) call this to read configurable timeouts and
     * browser settings without requiring callers to pass config explicitly.
     *
     * @deprecated Use {@link BBotSession#getConfig()} instead.
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public static BBotConfig getConfig() {
        return CURRENT_CONFIG;
    }

    /**
     * Returns the immutable {@link BBotSession} built by the last
     * {@link #register} / {@link #initialize} cycle.
     *
     * @throws IllegalStateException if the registry has not yet been initialised
     */
    public static BBotSession session() {
        BBotSession s = INSTANCE;
        if (s == null) throw new IllegalStateException(
            "BBotRegistry.session() called before initialize(). " +
            "Register descriptors and call initialize(config) in @BeforeAll first.");
        return s;
    }

    // ── DSL factory ───────────────────────────────────────────────────────────

    /**
     * Returns a fresh DSL for the named app, bound to the current scenario's page.
     * Invokes the descriptor's {@link DslFactory} — one fresh instance per scenario.
     *
     * @param appName  registered name, e.g. {@code "blotter"}
     * @param page     current scenario's Playwright page; {@code null} for REST-only apps
     * @param dslType  DSL class for type-safe cast (prevents accidental miscast)
     *
     * @deprecated Use {@link BBotSession#dsl(String, Page, Class)} instead.
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public static <D> D dsl(String appName, Page page, Class<D> dslType) {
        return requireSession().dsl(appName, page, dslType);
    }

    // ── Health / version assertions ───────────────────────────────────────────

    /**
     * Asserts the named app's health endpoint returns 2xx.
     * No-op if the descriptor declares no {@link AppDescriptor#healthCheckPath}.
     *
     * @deprecated Use {@link BBotSession#checkHealth(String)} instead.
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public static void checkHealth(String appName) {
        requireSession().checkHealth(appName);
    }

    /**
     * Asserts the named app's version endpoint returns JSON containing the expected version string.
     * No-op if the descriptor declares no {@link AppDescriptor#versionPath}.
     *
     * @deprecated Use {@link BBotSession#assertVersion(String, String)} instead.
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public static void assertVersion(String appName, String expectedVersion) {
        requireSession().assertVersion(appName, expectedVersion);
    }

    /** @deprecated Use {@link BBotSession#checkAllHealth()} instead. */
    @Deprecated(since = "1.1", forRemoval = true)
    public static void checkAllHealth() {
        requireSession().checkAllHealth();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** @deprecated Use instance-based lifecycle management instead. */
    @Deprecated(since = "1.1", forRemoval = true)
    public static void reset() {
        INSTANCE = null;
        BUILDER = null;
        CURRENT_CONFIG = null;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static BBotSession requireSession() {
        BBotSession s = INSTANCE;
        if (s == null) throw new IllegalStateException(
            "BBotRegistry not initialised. " +
            "Call BBotRegistry.register() + BBotRegistry.initialize(config) in @BeforeAll.");
        return s;
    }
}
