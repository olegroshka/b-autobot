package com.bbot.core.rest;

import com.bbot.core.exception.BBotConfigException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Instance-based key/value store for sharing values between Cucumber step
 * definitions within a single scenario.
 *
 * <p>This is the instance-based counterpart of the static {@link ScenarioState}.
 * When used with PicoContainer (or any DI framework), each scenario gets a
 * fresh {@code ScenarioContext} — no need to call {@link #reset()} manually.
 *
 * <p>Typical use:
 * <pre>{@code
 * // Step 1 — capture
 * scenarioContext.put("inquiry_id", responseField);
 *
 * // Step 2 — use in path via ${inquiry_id} token
 * String path = scenarioContext.resolve("/api/inquiry/${inquiry_id}/quote");
 *
 * // Step 3 — retrieve
 * String id = scenarioContext.require("inquiry_id");
 * }</pre>
 *
 * @see ScenarioState
 */
public final class ScenarioContext {

    private final Map<String, String> state = new HashMap<>();

    /** Stores {@code value} under {@code key} for the current scenario. */
    public void put(String key, String value) {
        state.put(key, value);
    }

    /** Returns the value for {@code key}, or empty if not yet captured. */
    public Optional<String> get(String key) {
        return Optional.ofNullable(state.get(key));
    }

    /**
     * Returns the value for {@code key}, or throws {@link BBotConfigException}
     * with a diagnostic message listing available keys.
     */
    public String require(String key) {
        return get(key).orElseThrow(() -> new BBotConfigException(
            "ScenarioContext: key '" + key + "' not found. " +
            "Ensure a previous step captured this value. " +
            "Available keys: " + state.keySet(), key));
    }

    /**
     * Clears all captured values.
     * When using PicoContainer, this is typically unnecessary because each
     * scenario gets a fresh {@code ScenarioContext} instance.
     */
    public void reset() {
        state.clear();
    }

    /**
     * Resolves all {@code ${key}} tokens in {@code template} from the current state.
     * Tokens with no matching key are left unchanged so the unresolved reference
     * is visible in any downstream error.
     *
     * @param template a string that may contain {@code ${key}} tokens
     * @return the string with all resolvable tokens substituted
     */
    public String resolve(String template) {
        String result = template;
        for (Map.Entry<String, String> e : state.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }
        return result;
    }
}

