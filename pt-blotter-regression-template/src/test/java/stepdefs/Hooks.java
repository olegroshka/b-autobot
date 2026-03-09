package stepdefs;

import com.bbot.core.PlaywrightManager;
import com.bbot.core.auth.SsoAuthConfig;
import com.bbot.core.auth.SsoAuthManager;
import com.bbot.core.config.BBotConfig;
import com.bbot.core.registry.BBotRegistry;
import com.bbot.core.registry.BBotSession;
import com.bbot.core.rest.HttpClientFactory;
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
 *   <li>For each application under test, declare {@code descriptor-class} in HOCON.
 *       {@link BBotSession.Builder#initialize} auto-discovers and instantiates them.</li>
 *   <li>If you need environment-specific overrides that are only known at runtime
 *       (e.g. dynamic ports from test containers), apply them via
 *       {@code BBotConfig.withOverrides(Map)} before calling
 *       {@code BBotSession.builder().initialize(cfg)}.</li>
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

    private static PlaywrightManager browser;

    @BeforeAll
    @SuppressWarnings("unused")
    public static void launchBrowser() {
        // Load HOCON config -- picks up application-{env}.conf automatically.
        BBotConfig cfg = BBotConfig.load();

        // Build an immutable BBotSession — auto-discovers descriptor classes declared
        // under b-bot.apps.*.descriptor-class in the active environment config.
        BBotSession session = BBotSession.builder()
                .initialize(cfg)
                .build();
        BBotRegistry.setSession(session);

        // SSO authentication — ensures a valid session before launching the browser.
        // mode=none (default) is a no-op; mode=auto/interactive prompts for login.
        SsoAuthConfig authConfig = SsoAuthConfig.from(cfg);
        SsoAuthManager.ensureAuthenticated(authConfig);

        // Browser lifecycle -- remove these lines if your suite is REST-only.
        browser = new PlaywrightManager(cfg);
        browser.initBrowser();
    }

    @Before
    public void openFreshContext() {
        // Each scenario gets its own isolated BrowserContext + Page.
        // PicoContainer provides a fresh ScenarioContext per scenario automatically.
        // Remove if REST-only.
        browser.initContext();
    }

    @After
    public void closeContext() {
        // Remove if REST-only.
        browser.closeContext();
    }

    @AfterAll
    @SuppressWarnings("unused")
    public static void shutdownBrowser() {
        browser.closeBrowser();
        HttpClientFactory.shutdown();
        BBotRegistry.clearSession();
    }
}
