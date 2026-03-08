package com.bbot.core.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry that holds the active {@link BBotSession}.
 *
 * <h2>Lifecycle — called from the consuming module's {@code Hooks.java}</h2>
 * <ol>
 *   <li>Build a {@link BBotSession} via {@link BBotSession#builder()} + {@link #setSession}</li>
 *   <li>{@link #session()} — returns the active session</li>
 *   <li>{@link #clearSession()} — in {@code @AfterAll} to release state</li>
 * </ol>
 *
 * <p>All domain operations are accessed via {@code session()} — there are no
 * convenience delegates on this class.
 */
public final class BBotRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(BBotRegistry.class);

    /** The immutable session produced by {@link #setSession}. */
    private static volatile BBotSession INSTANCE = null;

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
        LOG.info("Registry session set — {} app(s): {}", session.appNames().size(), session.appNames());
    }

    /**
     * Clears the active session.
     * Call in {@code @AfterAll} to release state between test class runs.
     */
    public static void clearSession() {
        INSTANCE = null;
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
}
