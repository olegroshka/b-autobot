package stepdefs;

import com.bbot.core.PlaywrightManager;
import com.bbot.core.config.BBotConfig;
import com.bbot.core.registry.BBotRegistry;
import descriptors.BlotterDescriptor;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;

/**
 * Cucumber lifecycle hooks for pt-blotter-regression.
 *
 * <p>No mock servers — all URLs come from the active environment config:
 * <ul>
 *   <li>{@code mvn verify}                         → {@code local} (default; needs services running locally)</li>
 *   <li>{@code mvn verify -Db-bot.env=devserver}   → {@code application-devserver.conf}</li>
 *   <li>{@code mvn verify -Db-bot.env=uat}         → {@code application-uat.conf}</li>
 * </ul>
 */
public class Hooks {

    @BeforeAll
    public static void launchBrowser() {
        // Load HOCON config — picks up application-{env}.conf automatically.
        BBotConfig cfg = BBotConfig.load();

        // Register descriptors — add more as the test suite grows.
        BBotRegistry.register(new BlotterDescriptor());
        BBotRegistry.initialize(cfg);

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
    }
}
