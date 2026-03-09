package com.bbot.core.registry;

import com.bbot.core.data.TestDataParser;

/**
 * Self-description of a tested application registered with {@link BBotSession.Builder}.
 *
 * <p>Implementations live in the consuming module (sandbox or real regression project).
 * Core never imports any concrete {@code AppDescriptor} — the dependency always flows inward.
 *
 * <p>The only thing a descriptor supplies is a {@link DslFactory} — everything else
 * (app name, health-check path, version path, URLs) comes from HOCON config:
 *
 * <pre>{@code
 * b-bot.apps.blotter {
 *   descriptor-class    = "descriptors.BlotterAppDescriptor"
 *   health-check-path   = "/api/health"
 *   version-path        = "/api/version"
 *   webUrl              = "http://localhost:9099/blotter/"
 *   apiBase             = "http://localhost:9099"
 * }
 * }</pre>
 *
 * <p>The app name is the HOCON key ({@code blotter} above).
 * {@link BBotSession.Builder#initialize(com.bbot.core.config.BBotConfig)} reads
 * {@code descriptor-class}, instantiates the descriptor via a public no-arg constructor,
 * and registers it under the app name automatically — no {@code register()} call needed
 * in {@code Hooks.java}.
 *
 * @param <D> the DSL type this descriptor produces via its {@link DslFactory}
 */
@FunctionalInterface
public interface AppDescriptor<D> {

    /**
     * Factory that constructs a fresh DSL instance for each scenario.
     *
     * <p>The {@link AppContext} provides all resolved config values (URLs, users,
     * timeouts) bound to this application. The {@link com.microsoft.playwright.Page}
     * is {@code null} for REST-only descriptors.
     */
    DslFactory<D> dslFactory();

    /**
     * Returns a parser for this app's domain-specific test data, or {@code null} if
     * the app has no test data.
     *
     * <p>When non-null, the parser is called once during {@link BBotSession.Builder#build()}
     * with the full resolved HOCON config. The result is stored in the app's
     * {@link AppContext} and retrieved via {@link AppContext#getTestData(Class)}.
     *
     * <p>The default implementation returns {@code null} — apps with no domain-specific
     * test data need not override this method.
     */
    default TestDataParser<?> testDataParser() { return null; }
}
