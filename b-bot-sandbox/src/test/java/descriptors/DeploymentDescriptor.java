package com.bbot.sandbox.descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.DslFactory;
import com.bbot.sandbox.utils.DeploymentDsl;

/**
 * Descriptor for the Deployment Dashboard — hybrid web + REST.
 * App name, health-check path, and URLs are declared in HOCON config.
 */
public final class DeploymentDescriptor implements AppDescriptor<DeploymentDsl> {

    @Override public DslFactory<DeploymentDsl> dslFactory() {
        return (ctx, page) -> new DeploymentDsl(page, ctx.getApiBaseUrl());
    }
}
