package stepdefs;

import com.bbot.core.PlaywrightManager;
import com.bbot.core.registry.BBotRegistry;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import utils.PtBlotterDsl;

/**
 * Step definitions for the real PT-Blotter regression scenarios.
 */
public class BlotterSteps {

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
