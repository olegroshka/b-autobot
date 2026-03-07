package descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.ComponentType;
import com.bbot.core.registry.DslFactory;
import utils.PtBlotterDsl;

import java.util.Optional;
import java.util.Set;

/**
 * {@link AppDescriptor} for the PT-Blotter application.
 *
 * <h2>Template customisation points</h2>
 * <ul>
 *   <li>Change {@link #name()} to match the key used in your
 *       {@code application-{env}.conf} ({@code b-bot.apps.<name>.*}).</li>
 *   <li>Change the generic type and DSL class to your own DSL implementation.</li>
 *   <li>Set {@link #healthCheckPath()} to whichever endpoint returns 2xx when
 *       the service is alive. Leave {@link Optional#empty()} to skip health checks.</li>
 *   <li>Set {@link #versionPath()} if you want
 *       {@link com.bbot.core.registry.BBotRegistry#assertVersion} to verify the
 *       deployed build. Leave {@link Optional#empty()} to skip version assertions.</li>
 *   <li>Declare {@link ComponentType#REST_API} only if your DSL makes HTTP calls
 *       (so the registry knows to validate the {@code apiBase} URL from config).</li>
 * </ul>
 *
 * <p>URLs are resolved at initialisation time from the active environment config
 * -- no hardcoded host names or ports in this class.
 */
public final class BlotterDescriptor implements AppDescriptor<PtBlotterDsl> {

    /** Must match the key under {@code b-bot.apps} in your conf files. */
    @Override public String name() { return "blotter"; }

    @Override public Set<ComponentType> componentTypes() {
        return Set.of(ComponentType.WEB_APP, ComponentType.REST_API);
    }

    @Override public DslFactory<PtBlotterDsl> dslFactory() {
        return (ctx, page) -> new PtBlotterDsl(page, ctx);
    }

    /** Path appended to apiBase for the liveness probe, e.g. GET /api/health -> 2xx. */
    @Override public Optional<String> healthCheckPath() {
        return Optional.of("/api/health");
    }

    /** Path appended to apiBase for version assertion, e.g. GET /api/version -> JSON. */
    @Override public Optional<String> versionPath() {
        return Optional.of("/api/version");
    }
}
