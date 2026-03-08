package com.bbot.core.rest;

import java.util.Optional;

/**
 * Thread-local key/value store for sharing values between Cucumber step definitions
 * within a single scenario.
 *
 * <p>Typical use: capture a field from one REST response and reference it in
 * the URL or assertion of the next step without coupling the two step classes.
 *
 * <pre>{@code
 * // Step 1 — capture
 * And I capture the response field "inquiry_id"
 *
 * // Step 2 — use in path via ${inquiry_id} token
 * When I GET from app "blotter" path "/api/inquiry/${inquiry_id}/quote"
 *
 * // Step 3 — use in assertion
 * Then the response field "status" should be "QUOTED"
 * }</pre>
 *
 * <p>Reset at the start of every Cucumber scenario by calling {@link #reset()}
 * from a {@code @Before} hook.
 *
 * <p>Since M11, the static methods delegate to a thread-local
 * {@link ScenarioContext} instance. New code should prefer injecting a
 * {@code ScenarioContext} via PicoContainer instead.
 *
 * @see ScenarioContext
 */
public final class ScenarioState {

    private static final ThreadLocal<ScenarioContext> CTX =
            ThreadLocal.withInitial(ScenarioContext::new);

    private ScenarioState() {}

    /**
     * Returns the thread-local {@link ScenarioContext} backing this static API.
     * Useful for bridge code that needs both APIs during the migration period.
     */
    public static ScenarioContext current() {
        return CTX.get();
    }

    /** @deprecated Use {@link ScenarioContext#put(String, String)} instead. */
    @Deprecated(since = "1.1", forRemoval = true)
    public static void put(String key, String value) {
        CTX.get().put(key, value);
    }

    /** @deprecated Use {@link ScenarioContext#get(String)} instead. */
    @Deprecated(since = "1.1", forRemoval = true)
    public static Optional<String> get(String key) {
        return CTX.get().get(key);
    }

    /**
     * Returns the value for {@code key}, or throws with a diagnostic message
     * listing available keys.
     *
     * @deprecated Use {@link ScenarioContext#require(String)} instead.
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public static String require(String key) {
        return CTX.get().require(key);
    }

    /** @deprecated Use instance-based {@link ScenarioContext} via PicoContainer instead. */
    @Deprecated(since = "1.1", forRemoval = true)
    public static void reset() {
        CTX.get().reset();
    }

    /**
     * Resolves all {@code ${key}} tokens in {@code template} from the current state.
     *
     * @deprecated Use {@link ScenarioContext#resolve(String)} instead.
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public static String resolve(String template) {
        return CTX.get().resolve(template);
    }
}
