package stepdefs;

import com.bbot.core.PlaywrightManager;
import com.bbot.core.config.BBotConfig;
import com.bbot.core.registry.BBotRegistry;
import descriptors.BlotterDescriptor;
import descriptors.ConfigServiceDescriptor;
import descriptors.DeploymentDescriptor;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;

/**
 * Cucumber lifecycle hooks — the single entry point that wires together
 * b-bot-core infrastructure for this regression suite.
 *
 * <h2>Template customisation points</h2>
 * <ul>
 *   <li>Register additional {@link com.bbot.core.registry.AppDescriptor}s
 *       for every application your suite tests (one descriptor per app).</li>
 *   <li>If you need environment-specific overrides that are only known at
 *       runtime (e.g. dynamic ports from test containers), apply them via
 *       {@link BBotConfig#withOverrides(java.util.Map)} before calling
 *       {@link BBotRegistry#initialize(BBotConfig)}.</li>
 *   <li>If your suite does not use a browser (REST-only), remove the
 *       {@link PlaywrightManager} calls.</li>
 * </ul>
 *
 * <h2>Active environment</h2>
 * Controlled by the {@code b-bot.env} system property:
 * <pre>
 *   mvn verify -Db-bot.env=mockuat     -- application-mockuat.conf (mock UAT stack)
 *   mvn verify -Db-bot.env=devserver   -- application-devserver.conf (blotter only)
 *   mvn verify -Db-bot.env=uat         -- application-uat.conf (real UAT, not committed)
 * </pre>
 *
 * <h2>Browser settings</h2>
 * Override any b-bot-core default in your env conf or on the CLI:
 * <pre>
 *   -Db-bot.browser.headless=false       -- open a real Chrome window
 *   -Db-bot.browser.type=firefox         -- use Firefox instead of Chromium
 *   -Db-bot.browser.viewport.width=1280  -- narrower viewport
 * </pre>
 */
public class Hooks {

    @BeforeAll
    public static void launchBrowser() {
        // Load HOCON config -- picks up application-{env}.conf automatically.
        BBotConfig cfg = BBotConfig.load();

        // Register one descriptor per application under test.
        // Each descriptor declares: app name, DSL factory, health-check path.
        BBotRegistry.register(new BlotterDescriptor());
        BBotRegistry.register(new ConfigServiceDescriptor());
        BBotRegistry.register(new DeploymentDescriptor());
        // BBotRegistry.register(new MyOtherServiceDescriptor());

        // Resolve AppContexts from config (URLs, users, timeouts).
        BBotRegistry.initialize(cfg);

        // Browser lifecycle -- remove these lines if your suite is REST-only.
        PlaywrightManager.initBrowser();
    }

    @Before
    public void openFreshContext() {
        // Each scenario gets its own isolated BrowserContext + Page.
        // Remove if REST-only.
        PlaywrightManager.initContext();
    }

    @After
    public void closeContext() {
        // Remove if REST-only.
        PlaywrightManager.closeContext();
    }

    @AfterAll
    public static void shutdownBrowser() {
        PlaywrightManager.closeBrowser();
        BBotRegistry.reset();
    }
}
