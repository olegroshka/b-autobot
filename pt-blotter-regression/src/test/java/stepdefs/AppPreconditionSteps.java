package stepdefs;

import com.bbot.core.registry.BBotRegistry;
import io.cucumber.java.en.Given;

/**
 * Generic precondition steps backed by {@link BBotRegistry}.
 * Identical to the sandbox version — copy into any b-bot-core consumer.
 */
public class AppPreconditionSteps {

    @Given("the {string} app is healthy")
    public void appIsHealthy(String appName) {
        BBotRegistry.checkHealth(appName);
    }

    @Given("the {string} service is running at version {string}")
    public void serviceIsRunningAtVersion(String appName, String expectedVersion) {
        BBotRegistry.assertVersion(appName, expectedVersion);
    }
}
