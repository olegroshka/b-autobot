package stepdefs;

import com.bbot.core.PlaywrightManager;
import com.bbot.core.config.BBotConfig;
import com.bbot.core.registry.BBotRegistry;
import com.bbot.core.registry.BBotSession;
import com.bbot.core.rest.HttpClientFactory;
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
 *       overrides, build {@link BBotSession}, launch browser</li>
 *   <li>{@code @Before}    — open a fresh browser context + page per scenario</li>
 *   <li>{@code @After}     — close context + page</li>
 *   <li>{@code @AfterAll}  — close browser, clear session, stop mock servers</li>
 * </ol>
 *
 * <p>Real consumers (e.g. {@code pt-blotter-regression}) omit the mock server block and
 * supply environment-specific URLs via {@code application-uat.conf} instead of
 * {@link BBotConfig#withOverrides}.
 */
public class Hooks {

    private static PlaywrightManager browser;

    @BeforeAll
    @SuppressWarnings("unused")
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

        // 3. Build BBotSession and register it with BBotRegistry.
        BBotSession session = BBotSession.builder()
                .register(new BlotterAppDescriptor())
                .register(new ConfigServiceDescriptor())
                .register(new DeploymentDescriptor())
                .initialize(cfg)
                .build();
        BBotRegistry.setSession(session);

        // 4. Launch Playwright browser.
        browser = new PlaywrightManager(cfg);
        browser.initBrowser();
    }

    @Before
    public void openFreshContext() {
        browser.initContext();
    }

    @After
    public void closeContext() {
        browser.closeContext();
    }

    @AfterAll
    @SuppressWarnings("unused")
    public static void shutdownBrowser() {
        browser.closeBrowser();
        HttpClientFactory.shutdown();
        BBotRegistry.clearSession();
        MockBlotterServer.stop();
        MockDeploymentServer.stop();
        MockConfigServer.stop();
    }
}
