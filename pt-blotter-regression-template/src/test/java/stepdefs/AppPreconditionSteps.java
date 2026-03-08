package stepdefs;

import io.cucumber.java.en.Given;

/**
 * Generic precondition steps that delegate to {@link com.bbot.core.registry.BBotSession}
 * health probes via the injected {@link TestWorld}.
 *
 * <p>These steps are ready-to-use — no customisation needed. Simply declare the
 * relevant precondition at the top of any scenario that requires a live service:
 *
 * <pre>
 *   Given the "blotter" app is healthy
 *   Given the "config-service" app is healthy
 * </pre>
 */
public class AppPreconditionSteps {

    private final TestWorld world;

    private static final String ENV_HINT =
            "\n\nIs the mock UAT environment running?" +
            "\n  Unix/Mac: scripts/start-mock-uat.sh" +
            "\n  Windows:  scripts\\start-mock-uat.bat\n";

    public AppPreconditionSteps(TestWorld world) {
        this.world = world;
    }

    @Given("the {string} app is healthy")
    public void appIsHealthy(String appName) {
        try {
            world.session().checkHealth(appName);
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + ENV_HINT, e);
        } catch (Exception e) {
            throw new AssertionError(
                    "Health check failed for '" + appName + "': " + e.getMessage() + ENV_HINT, e);
        }
    }
}
