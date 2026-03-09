package com.bbot.core.data;

import com.typesafe.config.Config;

/**
 * Parses domain-specific test data from the {@code b-bot.test-data} config block.
 *
 * <p>Each app descriptor can supply its own parser type — a bond-blotter descriptor
 * parses bonds and portfolios, an FX descriptor might parse currency pairs and tenors.
 * This lets each application own its domain data schema without polluting core.
 *
 * <p>Returned by {@link com.bbot.core.registry.AppDescriptor#testDataParser()}.
 * The parsed result is stored in the app's {@link com.bbot.core.registry.AppContext}
 * and retrieved via {@link com.bbot.core.registry.AppContext#getTestData(Class)}.
 *
 * <h2>Example implementation</h2>
 * <pre>{@code
 * public final class BlotterTestDataParser implements TestDataParser<BlotterTestData> {
 *     \@Override
 *     public BlotterTestData parse(Config rootConfig) {
 *         // read from rootConfig.getConfig("b-bot.test-data") …
 *         return new BlotterTestData(bonds, portfolios, users);
 *     }
 * }
 * }</pre>
 *
 * @param <T> the domain-specific parsed result type
 */
@FunctionalInterface
public interface TestDataParser<T> {

    /**
     * Parses the raw HOCON root config into a typed domain object.
     *
     * @param rootConfig the full resolved HOCON config (not just the test-data sub-tree,
     *                   so the parser can read any key it needs)
     * @return the parsed domain object; must not be {@code null}
     */
    T parse(Config rootConfig);
}
