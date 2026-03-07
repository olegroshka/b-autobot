package stepdefs;

import com.bbot.core.registry.BBotRegistry;
import io.cucumber.java.en.Given;

/**
 * Generic precondition steps that delegate to {@link BBotRegistry} health probes.
 *
 * <p>These steps are ready-to-use — no customisation needed. Simply declare the
 * relevant precondition at the top of any scenario that requires a live service:
 *
 * <pre>
 *   Given the "blotter" app is healthy
 *   Given the "config-service" app is healthy
 * </pre>
 *
 * <p>The health check calls the URL declared in
 * {@link com.bbot.core.registry.AppDescriptor#healthCheckPath()}.
 *
 * <p>If the health check fails (connection refused or non-2xx), the error message
 * includes instructions for starting the mock UAT environment.
 */
public class AppPreconditionSteps {

    private static final String ENV_HINT =
            "\n\nIs the mock UAT environment running?" +
            "\n  Unix/Mac: scripts/start-mock-uat.sh" +
            "\n  Windows:  scripts\\start-mock-uat.bat\n";

    @Given("the {string} app is healthy")
    public void appIsHealthy(String appName) {
        try {
            BBotRegistry.checkHealth(appName);
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + ENV_HINT, e);
        } catch (Exception e) {
            throw new AssertionError(
                    "Health check failed for '" + appName + "': " + e.getMessage() + ENV_HINT, e);
        }
    }
}
