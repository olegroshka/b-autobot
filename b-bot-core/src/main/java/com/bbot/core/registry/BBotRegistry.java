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
 *   <li>Build a {@link BBotSession} via {@link BBotSession#builder()} + {@link #setSession}</li>
 *   <li>{@link #session()} — returns the active session per scenario</li>
 *   <li>{@link #clearSession()} — in {@code @AfterAll} to release state</li>
 * </ol>
 *
 * <p>The static methods here are thin convenience wrappers over {@link BBotSession}.
 * Prefer the instance API for new code.
 */
public final class BBotRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(BBotRegistry.class);

    /** The immutable session produced by {@link #setSession}. */
    private static volatile BBotSession INSTANCE = null;
    /** Config reference kept for {@link #configOrNull()} convenience. */
    private static volatile BBotConfig CURRENT_CONFIG = null;

    private BBotRegistry() {}

    // ── Session management ────────────────────────────────────────────────────

    /**
     * Registers a pre-built {@link BBotSession} as the active session.
     *
     * <p>Typical usage in {@code @BeforeAll}:
     * <pre>{@code
     * BBotSession session = BBotSession.builder()
     *     .register(new BlotterDescriptor())
     *     .initialize(cfg)
     *     .build();
     * BBotRegistry.setSession(session);
     * }</pre>
     */
    public static void setSession(BBotSession session) {
        INSTANCE = session;
        CURRENT_CONFIG = session.getConfig();
        LOG.info("Registry session set — {} app(s): {}", session.appNames().size(), session.appNames());
    }

    /**
     * Clears the active session and config reference.
     * Call in {@code @AfterAll} to release state between test class runs.
     */
    public static void clearSession() {
        INSTANCE = null;
        CURRENT_CONFIG = null;
    }

    /**
     * Returns the {@link BBotConfig} from the active session, or {@code null}
     * if no session has been set yet.
     *
     * <p>Used by infrastructure classes (e.g. {@code PlaywrightManager}) that
     * fall back gracefully when config is not yet available.
     */
    public static BBotConfig configOrNull() {
        return CURRENT_CONFIG;
    }

    /**
     * Returns the immutable {@link BBotSession} built by the last
     * {@link #setSession} call.
     *
     * @throws IllegalStateException if the registry has not yet been initialised
     */
    public static BBotSession session() {
        BBotSession s = INSTANCE;
        if (s == null) throw new IllegalStateException(
            "BBotRegistry.session() called before initialize(). " +
            "Build a BBotSession via BBotSession.builder() and call BBotRegistry.setSession() in @BeforeAll.");
        return s;
    }

    // ── Convenience delegates — preferred: use session() directly ─────────────

    /**
     * Returns a fresh DSL for the named app.
     *
     * @see BBotSession#dsl(String, Page, Class)
     */
    public static <D> D dsl(String appName, Page page, Class<D> dslType) {
        return session().dsl(appName, page, dslType);
    }

    /**
     * Asserts the named app's health endpoint returns 2xx.
     *
     * @see BBotSession#checkHealth(String)
     */
    public static void checkHealth(String appName) {
        session().checkHealth(appName);
    }

    /**
     * Asserts the named app's version endpoint returns the expected version.
     *
     * @see BBotSession#assertVersion(String, String)
     */
    public static void assertVersion(String appName, String expectedVersion) {
        session().assertVersion(appName, expectedVersion);
    }

    /** @see BBotSession#checkAllHealth() */
    public static void checkAllHealth() {
        session().checkAllHealth();
    }
}
