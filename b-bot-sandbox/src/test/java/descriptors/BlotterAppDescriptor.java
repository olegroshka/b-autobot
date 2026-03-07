package descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.ComponentType;
import com.bbot.core.registry.DslFactory;
import utils.BlotterDsl;

import java.util.Optional;
import java.util.Set;

/** Descriptor for the PT-Blotter — a hybrid WEB_APP + REST_API component. */
public final class BlotterAppDescriptor implements AppDescriptor<BlotterDsl> {

    @Override public String name() { return "blotter"; }

    @Override public Set<ComponentType> componentTypes() {
        return Set.of(ComponentType.WEB_APP, ComponentType.REST_API);
    }

    @Override public DslFactory<BlotterDsl> dslFactory() {
        return (ctx, page) -> new BlotterDsl(page, ctx);
    }

    @Override public Optional<String> healthCheckPath() { return Optional.of("/api/health"); }
}
