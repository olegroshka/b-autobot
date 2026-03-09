package com.bbot.template.descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.DslFactory;
import com.bbot.template.utils.ConfigServiceDsl;

/**
 * {@link AppDescriptor} for the Config Service microservice.
 *
 * <p>REST-only: no browser interaction. Supplies only the DSL factory.
 * All environment-specific config lives in HOCON:
 * <pre>{@code
 * b-bot.apps.config-service {
 *   descriptor-class  = "com.bbot.template.descriptors.ConfigServiceDescriptor"
 *   health-check-path = "/api/config"
 *   apiBase           = "http://..."
 * }
 * }</pre>
 */
public final class ConfigServiceDescriptor implements AppDescriptor<ConfigServiceDsl> {

    @Override public DslFactory<ConfigServiceDsl> dslFactory() {
        return (ctx, page) -> new ConfigServiceDsl(ctx.getApiBaseUrl());
        // page is intentionally ignored — REST-only descriptor
    }
}
