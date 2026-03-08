package stepdefs;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import utils.DeploymentDsl;

/**
 * Step definitions for Deployment Dashboard scenarios.
 *
 * <p>REST-only — queries the deployment registry API directly via
 * {@link DeploymentDsl}. No browser interaction in these steps.
 */
public class DeploymentSteps {

    private final TestWorld world;
    private final DeploymentDsl deployment;

    private static final String ENV_HINT =
            "\n\nIs the mock UAT environment running?" +
            "\n  Unix/Mac: scripts/start-mock-uat.sh" +
            "\n  Windows:  scripts\\start-mock-uat.bat\n";

    public DeploymentSteps(TestWorld world) {
        this.world = world;
        this.deployment = world.session().dsl("deployment", null, DeploymentDsl.class);
    }

    /**
     * Connectivity check — asserts the deployment registry API is reachable
     * by fetching the service list and verifying a successful response.
     */
    @Given("the deployment dashboard is available")
    public void deploymentDashboardIsAvailable() {
        try {
            world.session().checkHealth("deployment");
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + ENV_HINT, e);
        } catch (Exception e) {
            throw new AssertionError(
                    "Deployment registry is not reachable: " + e.getMessage() + ENV_HINT, e);
        }
    }

    @Then("the deployment registry should list service {string}")
    public void deploymentShouldListService(String serviceName) {
        deployment.assertServiceInRegistry(serviceName);
    }

    @And("the service {string} is {string} at version {string}")
    public void serviceShouldBeAtVersion(String name, String status, String version) {
        deployment.assertServiceStatusAndVersion(name, status, version);
    }

    @And("the service {string} is {string} at its tested version")
    public void serviceShouldBeAtTestedVersion(String name, String status) {
        String version = world.session().getConfig().getTestData().getServiceVersion(name);
        deployment.assertServiceStatusAndVersion(name, status, version);
    }
}
