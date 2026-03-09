package com.bbot.template.descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.DslFactory;
import com.bbot.template.utils.DeploymentDsl;

/**
 * {@link AppDescriptor} for the Deployment Dashboard.
 *
 * <p>Hybrid WEB_APP + REST_API. Supplies only the DSL factory.
 * All environment-specific config lives in HOCON:
 * <pre>{@code
 * b-bot.apps.deployment {
 *   descriptor-class  = "com.bbot.template.descriptors.DeploymentDescriptor"
 *   health-check-path = "/api/deployments"
 *   webUrl            = "http://..."
 *   apiBase           = "http://..."
 * }
 * }</pre>
 */
public final class DeploymentDescriptor implements AppDescriptor<DeploymentDsl> {

    @Override public DslFactory<DeploymentDsl> dslFactory() {
        return (ctx, page) -> new DeploymentDsl(ctx.getApiBaseUrl());
    }
}
