package descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.ComponentType;
import com.bbot.core.registry.DslFactory;
import utils.DeploymentDsl;

import java.util.Optional;
import java.util.Set;

/** Descriptor for the Deployment Dashboard — a hybrid WEB_APP + REST_API component. */
public final class DeploymentDescriptor implements AppDescriptor<DeploymentDsl> {

    @Override public String name() { return "deployment"; }

    @Override public Set<ComponentType> componentTypes() {
        return Set.of(ComponentType.WEB_APP, ComponentType.REST_API);
    }

    @Override public DslFactory<DeploymentDsl> dslFactory() {
        return (ctx, page) -> new DeploymentDsl(page, ctx.getApiBaseUrl());
    }

    @Override public Optional<String> healthCheckPath() {
        return Optional.of("/api/deployments");
    }
}
