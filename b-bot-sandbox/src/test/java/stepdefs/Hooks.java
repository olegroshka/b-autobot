package stepdefs;

import com.bbot.core.PlaywrightManager;
import com.bbot.core.config.BBotConfig;
import com.bbot.core.registry.BBotRegistry;
import descriptors.BlotterAppDescriptor;
import descriptors.ConfigServiceDescriptor;
import descriptors.DeploymentDescriptor;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import utils.MockBlotterServer;
import utils.MockConfigServer;
import utils.MockDeploymentServer;

import java.util.Map;

/**
 * Cucumber lifecycle hooks for Playwright browser management and mock server lifecycle.
 *
 * <p>Execution order:
 * <ol>
 *   <li>{@code @BeforeAll} — start mock servers, build {@link BBotConfig} with dynamic port
 *       overrides, register descriptors, initialize {@link BBotRegistry}, launch browser</li>
 *   <li>{@code @Before}    — open a fresh browser context + page per scenario</li>
 *   <li>{@code @After}     — close context + page</li>
 *   <li>{@code @AfterAll}  — close browser, reset registry, stop mock servers</li>
 * </ol>
 *
 * <p>Real consumers (e.g. {@code pt-blotter-regression}) omit the mock server block and
 * supply environment-specific URLs via {@code application-uat.conf} instead of
 * {@link BBotConfig#withOverrides}.
 */
public class Hooks {

    @BeforeAll
    public static void launchBrowser() {
        // 1. Start mock servers (sandbox-only — real consumers omit this block)
        MockConfigServer.start();
        MockDeploymentServer.start();
        MockBlotterServer.start();

        // 2. Load base HOCON config, then layer dynamic mock-server ports on top.
        //    withOverrides() is immutable — returns a new BBotConfig instance.
        BBotConfig cfg = BBotConfig.load()
            .withOverrides(Map.of(
                "b-bot.apps.blotter.webUrl",          MockBlotterServer.getBlotterUrl(),
                "b-bot.apps.blotter.apiBase",         MockBlotterServer.getBaseUrl(),
                "b-bot.apps.config-service.apiBase",  MockConfigServer.getBaseUrl(),
                "b-bot.apps.deployment.webUrl",       MockDeploymentServer.getBaseUrl() + "/deployment/",
                "b-bot.apps.deployment.apiBase",      MockDeploymentServer.getBaseUrl()
            ));

        // 3. Register descriptors + resolve AppContexts from config.
        BBotRegistry.register(new BlotterAppDescriptor());
        BBotRegistry.register(new ConfigServiceDescriptor());
        BBotRegistry.register(new DeploymentDescriptor());
        BBotRegistry.initialize(cfg);

        // 4. Launch Playwright browser.
        PlaywrightManager.initBrowser();
    }

    @Before
    public void openFreshContext() {
        PlaywrightManager.initContext();
    }

    @After
    public void closeContext() {
        PlaywrightManager.closeContext();
    }

    @AfterAll
    public static void shutdownBrowser() {
        PlaywrightManager.closeBrowser();
        BBotRegistry.reset();
        MockBlotterServer.stop();
        MockDeploymentServer.stop();
        MockConfigServer.stop();
    }
}
