package descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.ComponentType;
import com.bbot.core.registry.DslFactory;
import utils.ConfigServiceDsl;

import java.util.Optional;
import java.util.Set;

/** Descriptor for the Config Service — a REST-only component. */
public final class ConfigServiceDescriptor implements AppDescriptor<ConfigServiceDsl> {

    @Override public String name() { return "config-service"; }

    @Override public Set<ComponentType> componentTypes() {
        return Set.of(ComponentType.REST_API);
    }

    @Override public DslFactory<ConfigServiceDsl> dslFactory() {
        return (ctx, page) -> new ConfigServiceDsl(ctx.getApiBaseUrl());
    }

    @Override public Optional<String> healthCheckPath() {
        return Optional.of("/api/config");
    }
}
