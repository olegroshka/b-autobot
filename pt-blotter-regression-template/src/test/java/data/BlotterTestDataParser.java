package com.bbot.template.data;

import com.bbot.core.data.Bond;
import com.bbot.core.data.Portfolio;
import com.bbot.core.data.TestDataConfig;
import com.bbot.core.data.TestDataParser;
import com.typesafe.config.Config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses blotter-specific test data from the {@code b-bot.test-data} config block.
 *
 * <p>Delegates to {@link TestDataConfig} for individual bond and portfolio lookups,
 * iterating the HOCON config to discover keys. The result is an immutable
 * {@link BlotterTestData} record stored in the blotter's {@link com.bbot.core.registry.AppContext}.
 *
 * <p>Registered on {@link descriptors.BlotterDescriptor#testDataParser()}.
 */
public final class BlotterTestDataParser implements TestDataParser<BlotterTestData> {

    @Override
    public BlotterTestData parse(Config rootConfig) {
        TestDataConfig tdc = new TestDataConfig(rootConfig);

        return new BlotterTestData(
            parseBonds(rootConfig, tdc),
            parsePortfolios(rootConfig, tdc),
            parseStringBlock(rootConfig, "b-bot.test-data.service-versions"),
            parseStringBlock(rootConfig, "b-bot.test-data.users"),
            tdc.getAllGlobals()
        );
    }

    private static Map<String, Bond> parseBonds(Config root, TestDataConfig tdc) {
        String path = "b-bot.test-data.bonds";
        if (!root.hasPath(path)) return Map.of();
        Map<String, Bond> result = new LinkedHashMap<>();
        root.getConfig(path).root().keySet().forEach(id -> result.put(id, tdc.getBond(id)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Portfolio> parsePortfolios(Config root, TestDataConfig tdc) {
        String path = "b-bot.test-data.portfolios";
        if (!root.hasPath(path)) return Map.of();
        Map<String, Portfolio> result = new LinkedHashMap<>();
        root.getConfig(path).root().keySet().stream()
            .filter(k -> !k.startsWith("_"))   // skip HOCON helper blocks (e.g. _cancel-lines-*)
            .forEach(name -> result.put(name, tdc.getPortfolio(name)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> parseStringBlock(Config root, String path) {
        if (!root.hasPath(path)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        root.getConfig(path).entrySet()
            .forEach(e -> result.put(e.getKey(), e.getValue().unwrapped().toString()));
        return Collections.unmodifiableMap(result);
    }
}
