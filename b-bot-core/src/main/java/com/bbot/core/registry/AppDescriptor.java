package com.bbot.core.registry;

import java.util.Optional;
import java.util.Set;

/**
 * Self-description of a tested component registered with {@link BBotSession.Builder}.
 *
 * <p>Implementations live in the consuming module (sandbox or real regression project).
 * Core never imports any concrete {@code AppDescriptor} — the dependency always flows inward.
 *
 * <p><b>Config key convention:</b> {@link #name()} must match the HOCON key at
 * {@code b-bot.apps.{name}.*}.
 *
 * @param <D> the DSL type this descriptor produces via its {@link DslFactory}
 */
@SuppressWarnings( "unused")
public interface AppDescriptor<D> {

    /**
     * Logical name — used as HOCON config key and step-definition reference.
     * Examples: {@code "blotter"}, {@code "config-service"}, {@code "deployment"}
     */
    String name();

    /** What kind of component this is. A hybrid app (web UI + REST API) declares both. */
    Set<ComponentType> componentTypes();

    /** Factory that constructs a fresh DSL instance for each scenario. */
    DslFactory<D> dslFactory();

    /**
     * Optional REST path for a liveness check.
     * {@code GET {apiBase}{path}} must return 2xx.
     * Enables: {@code "Given the blotter app is healthy"}
     */
    default Optional<String> healthCheckPath() { return Optional.empty(); }

    /**
     * Optional REST path for version discovery.
     * {@code GET {apiBase}{path}} must return JSON containing a {@code "version"} string field.
     * Enables: {@code "Given the blotter service is running at version v2.4.1"}
     */
    default Optional<String> versionPath() { return Optional.empty(); }
}
