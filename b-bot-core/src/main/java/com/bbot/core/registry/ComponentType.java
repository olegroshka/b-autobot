package com.bbot.core.registry;

/**
 * Classifies what kind of component an {@link AppDescriptor} represents.
 * A hybrid app (e.g. a web UI backed by a REST API) declares both types.
 */
public enum ComponentType {
    /** Has a browser-navigable UI; DSL will receive a live Playwright {@code Page}. */
    WEB_APP,
    /** Has a REST API base URL; DSL uses JDK HttpClient or Playwright {@code request()}. */
    REST_API
}
