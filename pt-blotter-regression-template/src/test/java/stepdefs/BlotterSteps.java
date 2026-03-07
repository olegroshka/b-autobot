package stepdefs;

import com.bbot.core.PlaywrightManager;
import com.bbot.core.registry.BBotRegistry;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import utils.PtBlotterDsl;

/**
 * Step definitions for PT-Blotter scenarios.
 *
 * <h2>Template customisation points</h2>
 * <ul>
 *   <li>Rename this class to match your application (e.g. {@code TradingDeskSteps}).</li>
 *   <li>Replace the DSL type with your own DSL class.</li>
 *   <li>Add {@code @Given} / {@code @When} / {@code @Then} methods for each
 *       Gherkin step in your feature files -- delegate entirely to the DSL.</li>
 *   <li>Never call Playwright directly from here; keep all locator knowledge
 *       inside the DSL class.</li>
 * </ul>
 */
public class BlotterSteps {

    // DSL is instantiated fresh for every scenario via BBotRegistry.
    private final PtBlotterDsl blotter =
            BBotRegistry.dsl("blotter", PlaywrightManager.getPage(), PtBlotterDsl.class);

    @Given("the PT-Blotter is open")
    public void ptBlotterIsOpen() {
        blotter.openBlotter();
    }

    @Given("the PT-Blotter is open as {string}")
    public void ptBlotterIsOpenAs(String user) {
        blotter.openBlotter(user);
    }

    @Then("the blotter grid should be visible")
    public void blotterGridShouldBeVisible() {
        blotter.assertGridRendered();
    }
}
