package com.bbot.sandbox.descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.DslFactory;
import com.bbot.sandbox.utils.BlotterDsl;

/**
 * Descriptor for the PT-Blotter — supplies only the DSL factory.
 * App name, health-check path, and URLs are declared in HOCON config.
 */
public final class BlotterAppDescriptor implements AppDescriptor<BlotterDsl> {

    @Override public DslFactory<BlotterDsl> dslFactory() {
        return (ctx, page) -> new BlotterDsl(page, ctx);
    }
}
