package com.bbot.core.rest;

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
 * <p>Reset at the start of every Cucumber scenario by calling
 * {@link ScenarioContext#reset()} via {@link #current()} from a {@code @Before} hook.
 *
 * <p>Use {@link #current()} to obtain the thread-local {@link ScenarioContext}.
 * New code should prefer injecting a {@code ScenarioContext} via PicoContainer instead.
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

}
