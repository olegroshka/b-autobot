package stepdefs;

import com.bbot.core.registry.BBotRegistry;
import io.cucumber.java.en.Given;

/**
 * Generic precondition steps that delegate to {@link BBotRegistry} health and
 * version probes.
 *
 * <p>These steps are ready-to-use -- no customisation needed. Simply declare the
 * relevant precondition at the top of any scenario that requires a live service:
 *
 * <pre>
 *   Given the "blotter" app is healthy
 *   Given the "blotter" service is running at version "2.4.1"
 * </pre>
 *
 * <p>The health check calls the URL declared in
 * {@link com.bbot.core.registry.AppDescriptor#healthCheckPath()}.
 * The version check calls {@link com.bbot.core.registry.AppDescriptor#versionPath()}
 * and looks for the expected version string in the JSON response.
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
