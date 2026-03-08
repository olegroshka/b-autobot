package stepdefs;

import com.bbot.core.PlaywrightManager;
import com.bbot.core.registry.BBotRegistry;
import com.bbot.core.registry.BBotSession;
import com.bbot.core.rest.ScenarioContext;
import com.microsoft.playwright.Page;

/**
 * PicoContainer-managed "world" object shared across all step definition classes
 * within a single Cucumber scenario.
 *
 * <p>PicoContainer creates one {@code TestWorld} per scenario and injects it into
 * every step definition class that declares it as a constructor parameter.
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li>{@link BBotSession} — immutable, shared across all scenarios</li>
 *   <li>{@link ScenarioContext} — fresh per scenario for inter-step value sharing</li>
 *   <li>{@link Page} — current scenario's Playwright page</li>
 * </ul>
 */
public class TestWorld {

    private final BBotSession session;
    private final ScenarioContext scenarioContext;
    private final PlaywrightManager playwrightManager;

    public TestWorld() {
        this.session = BBotRegistry.session();
        this.scenarioContext = new ScenarioContext();
        this.playwrightManager = new PlaywrightManager(session.getConfig());
    }

    /** Returns the immutable session built during {@code @BeforeAll}. */
    public BBotSession session() {
        return session;
    }

    /** Returns the per-scenario context for capturing/resolving inter-step values. */
    public ScenarioContext scenarioContext() {
        return scenarioContext;
    }

    /** Returns the current scenario's Playwright page. */
    public Page page() {
        return playwrightManager.getPage();
    }
}
