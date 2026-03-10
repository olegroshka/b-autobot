package com.bbot.sandbox.descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.DslFactory;
import com.bbot.sandbox.utils.ConfigServiceDsl;

/**
 * Descriptor for the Config Service — REST-only; no browser interaction.
 * App name, health-check path, and URLs are declared in HOCON config.
 */
public final class ConfigServiceDescriptor implements AppDescriptor<ConfigServiceDsl> {

    @Override public DslFactory<ConfigServiceDsl> dslFactory() {
        return (ctx, page) -> new ConfigServiceDsl(ctx);
    }
}
