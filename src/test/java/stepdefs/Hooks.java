package stepdefs;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import utils.MockBlotterServer;
import utils.MockConfigServer;
import utils.MockDeploymentServer;
import utils.PlaywrightManager;

/**
 * Cucumber lifecycle hooks for Playwright browser management and WireMock lifecycle.
 *
 * <p>Execution order:
 * <ol>
 *   <li>{@code @BeforeAll} — start WireMock mock server, then launch Playwright browser</li>
 *   <li>{@code @Before}    — open a fresh browser context + page per scenario</li>
 *   <li>{@code @After}     — close context + page</li>
 *   <li>{@code @AfterAll}  — close browser, then stop WireMock</li>
 * </ol>
 */
public class Hooks {

    @BeforeAll
    public static void launchBrowser() {
        MockConfigServer.start();
        MockDeploymentServer.start();
        MockBlotterServer.start();
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
        MockBlotterServer.stop();
        MockDeploymentServer.stop();
        MockConfigServer.stop();
    }
}
