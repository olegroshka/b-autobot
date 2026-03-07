package com.bbot.core.data;

import com.typesafe.config.Config;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Typed view over the {@code b-bot.test-data} HOCON block.
 *
 * <p>Provides access to three kinds of test-data entries:
 * <ul>
 *   <li><b>Global variables</b> — scalar values referenced as {@code ${key}} in templates,
 *       e.g. {@code settlement-date = "2026-03-21"}</li>
 *   <li><b>Bond lists</b> — named groups of ISINs and related identifiers, e.g.
 *       {@code bond-lists.HYPT_1.ISIN1 = "XS2346573523"}</li>
 *   <li><b>Template registry</b> — maps short names to classpath resource paths, e.g.
 *       {@code templates.credit-rfq = "templates/credit-rfq.json"}</li>
 * </ul>
 *
 * <h2>HOCON structure</h2>
 * <pre>{@code
 * b-bot.test-data {
 *   settlement-date = "2026-03-21"
 *
 *   bond-lists {
 *     HYPT_1 { ISIN1 = "XS2346573523", ISIN2 = "US912828YJ02" }
 *     IGPT_1 { ISIN1 = "GB0031348658" }
 *   }
 *
 *   templates {
 *     credit-rfq    = "templates/credit-rfq.json"
 *     quote-inquiry = "templates/quote-inquiry.json"
 *   }
 * }
 * }</pre>
 *
 * <p>Obtained via {@link com.bbot.core.config.BBotConfig#getTestData()}.
 */
public final class TestDataConfig {

    private static final String ROOT = "b-bot.test-data";

    private final Config cfg;

    /** Called only by {@link com.bbot.core.config.BBotConfig#getTestData()}. */
    public TestDataConfig(Config cfg) {
        this.cfg = cfg;
    }

    // ── Global variables ──────────────────────────────────────────────────────

    /**
     * Returns a global test-data variable by key, or empty if absent.
     * Example: {@code getGlobal("settlement-date")} → {@code Optional.of("2026-03-21")}.
     */
    public Optional<String> getGlobal(String key) {
        String path = ROOT + "." + key;
        if (!cfg.hasPath(path)) return Optional.empty();
        try {
            return Optional.of(cfg.getString(path));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Returns all global test-data scalars as a flat map.
     * Bond-list and template entries are excluded.
     * Used for bulk {@code ${key}} substitution in templates.
     */
    public Map<String, String> getAllGlobals() {
        if (!cfg.hasPath(ROOT)) return Map.of();
        Config root = cfg.getConfig(ROOT);
        Map<String, String> result = new LinkedHashMap<>();
        root.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("bond-lists")
                      && !e.getKey().startsWith("templates")
                      && !e.getKey().startsWith("api-actions")
                      && !e.getKey().startsWith("portfolios")
                      && !e.getKey().startsWith("service-versions")
                      && !e.getKey().startsWith("users")
                      && !e.getKey().startsWith("endpoints"))
            .forEach(e -> result.put(e.getKey(), e.getValue().unwrapped().toString()));
        return Collections.unmodifiableMap(result);
    }

    // ── Bond lists ────────────────────────────────────────────────────────────

    /**
     * Returns all fields in the named bond list as an unmodifiable map.
     *
     * <p>Example: {@code getBondList("HYPT_1")} might return
     * {@code {ISIN1=XS2346573523, ISIN2=US912828YJ02}}.
     *
     * @throws AssertionError if the bond list does not exist
     */
    public Map<String, String> getBondList(String name) {
        String path = ROOT + ".bond-lists." + name;
        if (!cfg.hasPath(path))
            throw new AssertionError(
                "Bond list '" + name + "' not found in b-bot.test-data.bond-lists. " +
                "Define it in your application-{env}.conf.");
        Map<String, String> result = new LinkedHashMap<>();
        cfg.getConfig(path).entrySet().forEach(e ->
            result.put(e.getKey(), e.getValue().unwrapped().toString()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Resolves a bond-list field reference: {@code resolveBondRef("HYPT_1", "ISIN1")} → actual ISIN.
     *
     * @throws AssertionError if the bond list or field does not exist
     */
    public String resolveBondRef(String bondListName, String fieldName) {
        Map<String, String> list = getBondList(bondListName);
        String value = list.get(fieldName);
        if (value == null)
            throw new AssertionError(
                "Field '" + fieldName + "' not found in bond list '" + bondListName + "'. " +
                "Available fields: " + list.keySet());
        return value;
    }

    // ── Template registry ─────────────────────────────────────────────────────

    /**
     * Returns the classpath resource path for the named template.
     *
     * <p>Example: {@code getTemplatePath("credit-rfq")} → {@code "templates/credit-rfq.json"}.
     *
     * @throws AssertionError if the template name is not registered
     */
    public String getTemplatePath(String templateName) {
        String path = ROOT + ".templates." + templateName;
        if (!cfg.hasPath(path))
            throw new AssertionError(
                "Template '" + templateName + "' not registered in b-bot.test-data.templates. " +
                "Add:  templates." + templateName + " = \"path/to/file.json\"");
        return cfg.getString(path);
    }
    // ── Service versions ──────────────────────────────────────────────────────

    /**
     * Returns the tested version string for the named service from
     * {@code b-bot.test-data.service-versions}.
     *
     * <p>Example: {@code getServiceVersion("credit-rfq-blotter")} → {@code "v2.4.1"}.
     *
     * @throws AssertionError if the service name is not declared
     */
    public String getServiceVersion(String serviceName) {
        String path = ROOT + ".service-versions." + serviceName;
        if (!cfg.hasPath(path))
            throw new AssertionError(
                "Service version for '" + serviceName + "' not found in b-bot.test-data.service-versions. " +
                "Add:  service-versions.\"" + serviceName + "\" = \"vX.Y.Z\"");
        return cfg.getString(path);
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    /**
     * Returns the username for the named role from {@code b-bot.test-data.users}.
     *
     * <p>Example: {@code getUser("trader")} → {@code "doej"},
     * {@code getUser("admin")} → {@code "smithj"}.
     *
     * @throws AssertionError if the role is not declared
     */
    public String getUser(String role) {
        String path = ROOT + ".users." + role;
        if (!cfg.hasPath(path))
            throw new AssertionError(
                "User role '" + role + "' not found in b-bot.test-data.users. " +
                "Add:  users." + role + " = \"username\"");
        return cfg.getString(path);
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * Returns the URL for the named endpoint from {@code b-bot.test-data.endpoints}.
     *
     * <p>Use this for external or well-known URLs that should not be hardcoded in
     * feature files, e.g. {@code getEndpoint("finance-demo")} → the AG Grid demo URL.
     *
     * @throws AssertionError if the endpoint name is not declared
     */
    public String getEndpoint(String name) {
        String path = ROOT + ".endpoints." + name;
        if (!cfg.hasPath(path))
            throw new AssertionError(
                "Endpoint '" + name + "' not found in b-bot.test-data.endpoints. " +
                "Add:  endpoints." + name + " = \"https://...\"");
        return cfg.getString(path);
    }

    // ── API actions ───────────────────────────────────────────────────────────

    /**
     * Returns the named API action from {@code b-bot.test-data.api-actions}.
     *
     * @throws AssertionError if the action is not declared
     */
    public ApiAction getApiAction(String name) {
        String path = ROOT + ".api-actions." + name;
        if (!cfg.hasPath(path))
            throw new AssertionError(
                "API action '" + name + "' not found in b-bot.test-data.api-actions. " +
                "Declare it in your application-{env}.conf.");
        Config c = cfg.getConfig(path);
        return new ApiAction(
            name,
            c.getString("method"),
            c.getString("app"),
            c.getString("path"),
            c.hasPath("template") ? c.getString("template") : null
        );
    }

    // ── Portfolios ────────────────────────────────────────────────────────────

    /**
     * Returns the named portfolio from {@code b-bot.test-data.portfolios}.
     * Bond entries are returned in declaration order (HOCON key-sort order).
     *
     * @throws AssertionError if the portfolio is not declared
     */
    public Portfolio getPortfolio(String name) {
        String root = ROOT + ".portfolios." + name;
        if (!cfg.hasPath(root))
            throw new AssertionError(
                "Portfolio '" + name + "' not found in b-bot.test-data.portfolios. " +
                "Declare it in your application-{env}.conf.");
        Config pc = cfg.getConfig(root);

        String ptId = pc.getString("pt-id");
        String settlementDate = pc.hasPath("settlement-date")
                ? pc.getString("settlement-date")
                : getGlobal("settlement-date").orElse("");

        LinkedHashMap<String, PortfolioBond> bonds = new LinkedHashMap<>();
        if (pc.hasPath("bonds")) {
            Config bc = pc.getConfig("bonds");
            bc.root().keySet().stream().sorted().forEach(key -> {
                Config b = bc.getConfig(key);
                bonds.put(key, new PortfolioBond(
                    b.getString("isin"),
                    b.getLong("quantity"),
                    b.getString("side"),
                    b.hasPath("currency") ? b.getString("currency") : "USD"
                ));
            });
        }
        return new Portfolio(name, ptId, settlementDate, bonds);
    }

}
