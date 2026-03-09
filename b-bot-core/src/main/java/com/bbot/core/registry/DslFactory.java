package com.bbot.core.registry;

import com.microsoft.playwright.Page;

/**
 * Creates the DSL for one tested component, given its resolved runtime context
 * and (for browser-based components) the current scenario's Playwright page.
 *
 * <p>{@code Page} is per-scenario (created in {@code @Before});
 * {@link AppContext} is per-environment (resolved once in {@code @BeforeAll}).
 * Keeping them separate lets the same factory be registered once and called
 * fresh each scenario.
 *
 * @param <D> the DSL type produced by this factory
 */
@FunctionalInterface
public interface DslFactory<D> {

    /**
     * @param ctx  resolved environment context — never {@code null}
     * @param page current scenario's Playwright page — {@code null} for REST-only apps
     */
    D create(AppContext ctx, Page page);
}
