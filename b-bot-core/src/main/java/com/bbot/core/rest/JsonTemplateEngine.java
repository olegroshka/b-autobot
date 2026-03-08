package com.bbot.core.rest;

import com.bbot.core.data.TestDataConfig;
import com.bbot.core.exception.BBotTemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads JSON template files from the classpath and substitutes {@code ${token}}
 * placeholders with values from test-data config and scenario state.
 *
 * <h2>Token resolution order (first match wins)</h2>
 * <ol>
 *   <li>{@link ScenarioContext} — values captured from previous steps, e.g.
 *       {@code ${inquiry_id}}</li>
 *   <li>Active bond list — fields prefixed with {@code bond.}, e.g.
 *       {@code ${bond.ISIN1}} when bond list "HYPT_1" is active</li>
 *   <li>Global test-data scalars — top-level {@code b-bot.test-data} keys, e.g.
 *       {@code ${settlement-date}}</li>
 * </ol>
 *
 * <p>Unresolved tokens are left in place so the missing variable name is clearly
 * visible in any JSON parse error downstream.
 *
 * <h2>Template file example ({@code templates/credit-rfq.json})</h2>
 * <pre>{@code
 * {
 *   "isin":           "${bond.ISIN1}",
 *   "settlementDate": "${settlement-date}",
 *   "quantity":       1000000,
 *   "side":           "Buy"
 * }
 * }</pre>
 *
 * <p>Obtain via {@code new JsonTemplateEngine(config.getTestData(), scenarioContext)}.
 */
public final class JsonTemplateEngine {

    private static final Logger LOG = LoggerFactory.getLogger(JsonTemplateEngine.class);

    private final TestDataConfig testData;
    private final ScenarioContext ctx;

    /**
     * Creates a template engine that resolves {@code ${key}} tokens from the
     * given scenario context.
     *
     * @param testData test data configuration
     * @param ctx      scenario context for token resolution
     */
    public JsonTemplateEngine(TestDataConfig testData, ScenarioContext ctx) {
        this.testData = testData;
        this.ctx = ctx;
    }


    /**
     * Renders the named template substituting tokens from the active bond list,
     * global test-data, and current scenario state.
     *
     * <p>The template path is resolved from {@code b-bot.test-data.templates.{name}}.
     *
     * @param templateName registered template name (e.g. {@code "credit-rfq"})
     * @param bondListName bond list name (e.g. {@code "HYPT_1"}); its fields are
     *                     accessible as {@code ${bond.FIELD}} in the template
     * @return rendered JSON string, ready to use as an HTTP request body
     */
    public String render(String templateName, String bondListName) {
        LOG.debug("Rendering template '{}' with bond list '{}'", templateName, bondListName);
        String raw = load(testData.getTemplatePath(templateName));
        return substitute(raw, testData.getBondList(bondListName), testData.getAllGlobals());
    }

    /**
     * Renders the named template using only global test-data and scenario state
     * (no bond list). Use this for templates that don't reference bond-specific fields.
     */
    public String render(String templateName) {
        LOG.debug("Rendering template '{}' (no bond list)", templateName);
        String raw = load(testData.getTemplatePath(templateName));
        return substitute(raw, Map.of(), testData.getAllGlobals());
    }

    /**
     * Renders the named template substituting tokens directly from the supplied
     * variable map, scenario context, and global test-data.
     *
     * <p>Use this for portfolio-level submission where each bond provides its
     * own variable context ({@code isin}, {@code quantity}, {@code side}, etc.)
     * rather than referencing a named bond list.
     *
     * @param templateName registered template name (e.g. {@code "portfolio-rfq"})
     * @param vars         additional variables that override globals; values are
     *                     substituted as {@code ${key}} tokens in the template
     */
    public String renderWithContext(String templateName, Map<String, String> vars) {
        LOG.debug("Rendering template '{}' with context vars: {}", templateName, vars.keySet());
        String raw = load(testData.getTemplatePath(templateName));
        return substituteWithContext(raw, vars, testData.getAllGlobals());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String load(String classpathPath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classpathPath)) {
            if (is == null)
                throw new BBotTemplateException(
                    "Template not found on classpath: '" + classpathPath + "'. " +
                    "Ensure the file is under src/test/resources/ in your module.",
                    classpathPath);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BBotTemplateException(
                    "Failed to load template: " + classpathPath, classpathPath, e);
        }
    }

    private String substitute(String raw, Map<String, String> bondList,
                               Map<String, String> globals) {
        String result = raw;

        // 1. Scenario state — ${key} for any captured value
        result = resolveCtx().resolve(result);

        // 2. Active bond list — ${bond.FIELD}
        for (Map.Entry<String, String> e : bondList.entrySet()) {
            result = result.replace("${bond." + e.getKey() + "}", e.getValue());
        }

        // 3. Global test-data scalars — ${key}
        for (Map.Entry<String, String> e : globals.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }

        return result;
    }
    private String substituteWithContext(String raw, Map<String, String> vars,
                                          Map<String, String> globals) {
        String result = raw;

        // 1. Scenario state — ${key}
        result = resolveCtx().resolve(result);

        // 2. Caller-supplied vars — ${key} (override globals)
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }

        // 3. Global test-data scalars — ${key}
        for (Map.Entry<String, String> e : globals.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }

        return result;
    }

    /** Returns the injected context for token resolution. */
    private ScenarioContext resolveCtx() {
        return ctx;
    }

}
