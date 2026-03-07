package com.bbot.core.rest;

import java.util.HashMap;
import java.util.Map;
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
 */
public final class ScenarioState {

    private static final ThreadLocal<Map<String, String>> STATE =
            ThreadLocal.withInitial(HashMap::new);

    private ScenarioState() {}

    /** Stores {@code value} under {@code key} for the current scenario. */
    public static void put(String key, String value) {
        STATE.get().put(key, value);
    }

    /** Returns the value for {@code key}, or empty if not yet captured. */
    public static Optional<String> get(String key) {
        return Optional.ofNullable(STATE.get().get(key));
    }

    /**
     * Returns the value for {@code key}, or throws {@link AssertionError} with a
     * diagnostic message listing available keys.
     */
    public static String require(String key) {
        return get(key).orElseThrow(() -> new AssertionError(
                "ScenarioState: key '" + key + "' not found. " +
                "Ensure a previous step captured this value. " +
                "Available keys: " + STATE.get().keySet()));
    }

    /**
     * Clears all captured values for the current thread.
     * Call from a Cucumber {@code @Before} hook so each scenario starts clean.
     */
    public static void reset() {
        STATE.get().clear();
    }

    /**
     * Resolves all {@code ${key}} tokens in {@code template} from the current state.
     * Tokens with no matching key are left unchanged so the unresolved reference
     * is visible in any downstream error.
     *
     * @param template a string that may contain {@code ${key}} tokens
     * @return the string with all resolvable tokens substituted
     */
    public static String resolve(String template) {
        String result = template;
        for (Map.Entry<String, String> e : STATE.get().entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }
        return result;
    }
}
