package com.bbot.core.data;

import com.bbot.core.exception.BBotConfigException;
import com.typesafe.config.Config;

import java.util.*;

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
                      && !e.getKey().startsWith("bonds")
                      && !e.getKey().startsWith("templates")
                      && !e.getKey().startsWith("portfolios")
                      && !e.getKey().startsWith("service-versions")
                      && !e.getKey().startsWith("users"))
            .forEach(e -> result.put(e.getKey(), e.getValue().unwrapped().toString()));
        return Collections.unmodifiableMap(result);
    }

    // ── Bond catalogue ────────────────────────────────────────────────────────

    /**
     * Returns the bond with the given catalogue ID from {@code b-bot.test-data.bonds}.
     *
     * <p>Example: {@code getBond("UST-2Y")} returns the {@link Bond} record with
     * {@code id="UST-2Y", isin="US912828YJ02", ...}.
     *
     * @throws BBotConfigException if the bond ID is not in the catalogue
     */
    public Bond getBond(String bondId) {
        String path = ROOT + ".bonds." + bondId;
        if (!cfg.hasPath(path))
            throw new BBotConfigException(
                "Bond '" + bondId + "' not found in b-bot.test-data.bonds. " +
                "Add it to the bonds catalogue in your application-{env}.conf.", path);
        Config b = cfg.getConfig(path);
        return new Bond(
            bondId,
            b.getString("isin"),
            b.hasPath("description") ? b.getString("description") : "",
            b.hasPath("maturity")    ? b.getString("maturity")    : "",
            b.hasPath("coupon")      ? b.getDouble("coupon")      : 0.0
        );
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
            throw new BBotConfigException(
                "Bond list '" + name + "' not found in b-bot.test-data.bond-lists. " +
                "Define it in your application-{env}.conf.", path);
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
            throw new BBotConfigException(
                "Field '" + fieldName + "' not found in bond list '" + bondListName + "'. " +
                "Available fields: " + list.keySet(),
                ROOT + ".bond-lists." + bondListName + "." + fieldName);
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
            throw new BBotConfigException(
                "Template '" + templateName + "' not registered in b-bot.test-data.templates. " +
                "Add:  templates." + templateName + " = \"path/to/file.json\"", path);
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
            throw new BBotConfigException(
                "Service version for '" + serviceName + "' not found in b-bot.test-data.service-versions. " +
                "Add:  service-versions.\"" + serviceName + "\" = \"vX.Y.Z\"", path);
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
            throw new BBotConfigException(
                "User role '" + role + "' not found in b-bot.test-data.users. " +
                "Add:  users." + role + " = \"username\"", path);
        return cfg.getString(path);
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
            throw new BBotConfigException(
                "Portfolio '" + name + "' not found in b-bot.test-data.portfolios. " +
                "Declare it in your application-{env}.conf.", root);
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
                // Resolve instrument fields from the bond catalogue if a reference is declared.
                String bondId  = b.hasPath("bond") ? b.getString("bond") : null;
                Bond   ref     = bondId != null ? getBond(bondId) : null;

                String isin    = b.hasPath("isin")        ? b.getString("isin")        : (ref != null ? ref.isin()        : "");
                String desc    = b.hasPath("description") ? b.getString("description") : (ref != null ? ref.description() : "");
                String mat     = b.hasPath("maturity")    ? b.getString("maturity")    : (ref != null ? ref.maturity()    : "");
                double coupon  = b.hasPath("coupon")      ? b.getDouble("coupon")      : (ref != null ? ref.coupon()      : 0.0);
                long   qty     = b.getLong("quantity");

                bonds.put(key, new PortfolioBond(
                    isin,
                    qty,
                    b.getString("side"),
                    b.hasPath("currency") ? b.getString("currency") : "USD",
                    desc,
                    mat,
                    coupon,
                    b.hasPath("notional") ? b.getLong("notional") : qty,
                    b.hasPath("client")   ? b.getString("client")  : "",
                    bondId
                ));
            });
        }
        return new Portfolio(name, ptId, settlementDate, bonds);
    }

}
