package descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.ComponentType;
import com.bbot.core.registry.DslFactory;
import utils.PtBlotterDsl;

import java.util.Optional;
import java.util.Set;

/**
 * AppDescriptor for the real PT-Blotter.
 *
 * <p>URLs are resolved from the active environment config
 * (e.g. {@code application-uat.conf}) — no hardcoded values here.
 */
public final class BlotterDescriptor implements AppDescriptor<PtBlotterDsl> {

    @Override public String name() { return "blotter"; }

    @Override public Set<ComponentType> componentTypes() {
        return Set.of(ComponentType.WEB_APP, ComponentType.REST_API);
    }

    @Override public DslFactory<PtBlotterDsl> dslFactory() {
        return (ctx, page) -> new PtBlotterDsl(page, ctx);
    }

    @Override public Optional<String> healthCheckPath() {
        return Optional.of("/api/health");
    }

    @Override public Optional<String> versionPath() {
        return Optional.of("/api/version");
    }
}
