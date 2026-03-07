package descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.ComponentType;
import com.bbot.core.registry.DslFactory;
import utils.DeploymentDsl;

import java.util.Optional;
import java.util.Set;

/**
 * {@link AppDescriptor} for the Deployment Dashboard.
 *
 * <p>Hybrid WEB_APP + REST_API: the registry validates both URLs are configured.
 * The template regression suite uses only the REST API for version/health evidence;
 * add browser scenarios and a browser DSL method if UI validation is also needed.
 */
public final class DeploymentDescriptor implements AppDescriptor<DeploymentDsl> {

    @Override public String name() { return "deployment"; }

    @Override public Set<ComponentType> componentTypes() {
        return Set.of(ComponentType.WEB_APP, ComponentType.REST_API);
    }

    @Override public DslFactory<DeploymentDsl> dslFactory() {
        return (ctx, page) -> new DeploymentDsl(ctx.getApiBaseUrl());
    }

    /** GET /api/deployments returns the full service list → 200 = registry alive. */
    @Override public Optional<String> healthCheckPath() {
        return Optional.of("/api/deployments");
    }
}
