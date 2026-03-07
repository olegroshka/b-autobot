package descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.ComponentType;
import com.bbot.core.registry.DslFactory;
import utils.ConfigServiceDsl;

import java.util.Optional;
import java.util.Set;

/**
 * {@link AppDescriptor} for the Config Service microservice.
 *
 * <p>REST-only: no browser interaction. The DSL makes JDK {@code HttpClient} calls
 * directly against {@code b-bot.apps.config-service.apiBase} from the active config.
 */
public final class ConfigServiceDescriptor implements AppDescriptor<ConfigServiceDsl> {

    @Override public String name() { return "config-service"; }

    @Override public Set<ComponentType> componentTypes() {
        return Set.of(ComponentType.REST_API);
    }

    @Override public DslFactory<ConfigServiceDsl> dslFactory() {
        return (ctx, page) -> new ConfigServiceDsl(ctx.getApiBaseUrl());
        // page is intentionally ignored — REST-only descriptor
    }

    /** GET /api/config returns a JSON array of namespaces → 200 = service alive. */
    @Override public Optional<String> healthCheckPath() {
        return Optional.of("/api/config");
    }
}
