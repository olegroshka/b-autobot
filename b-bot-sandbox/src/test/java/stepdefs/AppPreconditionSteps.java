package stepdefs;

import com.bbot.core.registry.BBotRegistry;
import io.cucumber.java.en.Given;

/**
 * Generic precondition steps backed by {@link BBotRegistry}.
 *
 * <p>These steps are environment-agnostic — they work against both mock servers
 * (sandbox) and real systems (UAT, pre-prod) because the health and version
 * checks delegate to the descriptor's declared endpoints.
 *
 * <p>Copy this class verbatim into any consumer project that uses {@code b-bot-core}.
 * No changes are needed — just register your descriptors in {@code Hooks.java}.
 */
public class AppPreconditionSteps {

    /**
     * Asserts the named app's health endpoint returns 2xx.
     * Example: {@code Given the "blotter" app is healthy}
     */
    @Given("the {string} app is healthy")
    public void appIsHealthy(String appName) {
        BBotRegistry.checkHealth(appName);
    }

    /**
     * Asserts the named service is running at the expected version.
     * Example: {@code Given the "blotter" service is running at version "v2.4.1"}
     */
    @Given("the {string} service is running at version {string}")
    public void serviceIsRunningAtVersion(String appName, String expectedVersion) {
        BBotRegistry.assertVersion(appName, expectedVersion);
    }
}
