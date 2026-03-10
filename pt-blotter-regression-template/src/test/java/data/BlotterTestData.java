package com.bbot.template.data;

import com.bbot.core.data.Bond;
import com.bbot.core.data.Portfolio;

import java.util.Map;

/**
 * Domain-specific parsed test data for the PT-Blotter application.
 *
 * <p>Populated by {@link BlotterTestDataParser} during {@link com.bbot.core.registry.BBotSession}
 * construction and accessible via:
 * <pre>{@code
 * BlotterTestData td = world.session().context("blotter").getTestData(BlotterTestData.class);
 * Bond bond = td.bond("UST-2Y");
 * Portfolio hypt = td.portfolio("HYPT_1");
 * }</pre>
 *
 * @param bonds           bond catalogue — keyed by catalogue ID (e.g. {@code "UST-2Y"})
 * @param portfolios      named test portfolios — keyed by portfolio name
 * @param serviceVersions service → expected version (from {@code service-versions} block)
 * @param users           role → username (from {@code users} block)
 * @param globals         flat scalar globals (e.g. {@code settlement-date})
 */
public record BlotterTestData(
        Map<String, Bond>      bonds,
        Map<String, Portfolio> portfolios,
        Map<String, String>    serviceVersions,
        Map<String, String>    users,
        Map<String, String>    globals
) {
    /** Returns the bond by catalogue ID, or throws if absent. */
    public Bond bond(String id) {
        Bond b = bonds.get(id);
        if (b == null) throw new AssertionError(
                "Bond '" + id + "' not found in BlotterTestData catalogue. " +
                "Available: " + bonds.keySet());
        return b;
    }

    /** Returns the portfolio by name, or throws if absent. */
    public Portfolio portfolio(String name) {
        Portfolio p = portfolios.get(name);
        if (p == null) throw new AssertionError(
                "Portfolio '" + name + "' not found in BlotterTestData. " +
                "Available: " + portfolios.keySet());
        return p;
    }

    /** Returns the username for the given role, or throws if absent. */
    public String user(String role) {
        String u = users.get(role);
        if (u == null) throw new AssertionError(
                "User role '" + role + "' not found in BlotterTestData. " +
                "Available roles: " + users.keySet());
        return u;
    }
}
