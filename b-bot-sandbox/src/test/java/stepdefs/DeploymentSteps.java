package stepdefs;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import com.bbot.core.PlaywrightManager;
import com.bbot.core.registry.BBotRegistry;
import utils.DeploymentDsl;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Deployment.feature and the @precondition scenario
 * in BondBlotter.feature.
 *
 * <p>API steps work without a browser.  Grid and filter steps require the
 * deployment webapp to have been built ({@code -Ddeployment.build.skip=false})
 * — the committed pre-built assets satisfy this for normal {@code mvn verify} runs.
 */
public class DeploymentSteps {

    /** API-only DSL (page=null). */
    private final DeploymentDsl api =
            BBotRegistry.dsl("deployment", null, DeploymentDsl.class);

    /** Browser DSL — lazily instantiated when a @grid or @filter step runs. */
    private DeploymentDsl ui;

    private DeploymentDsl ui() {
        if (ui == null)
            ui = BBotRegistry.dsl("deployment", PlaywrightManager.getPage(), DeploymentDsl.class);
        return ui;
    }

    // ── Precondition ──────────────────────────────────────────────────────────

    @Given("the deployment dashboard is available")
    public void deploymentDashboardIsAvailable() {
        BBotRegistry.checkHealth("deployment");
    }

    // ── API — service assertions ──────────────────────────────────────────────

    @Then("the dashboard should list at least {int} services")
    public void dashboardListsAtLeastServices(int min) throws IOException, InterruptedException {
        api.assertServiceCount(min);
    }

    @Then("the service {string} should be {string} at version {string}")
    public void serviceStatusAndVersion(String name, String status, String version)
            throws IOException, InterruptedException {
        api.assertServiceRunningAtVersion(name, status, version);
    }

    @Then("the service {string} should have status {string}")
    public void serviceStatus(String name, String status) throws IOException, InterruptedException {
        api.assertServiceStatus(name, status);
    }

    @Then("{int} services should have status {string}")
    public void serviceCountWithStatus(int count, String status)
            throws IOException, InterruptedException {
        api.assertStatusCount(status, count);
    }

    @Then("the deployment API should list service {string}")
    public void deploymentApiListsService(String name) throws IOException, InterruptedException {
        api.assertServicePresent(name);
    }

    // ── Browser — grid ────────────────────────────────────────────────────────

    @Given("the deployment dashboard is open")
    public void openDeploymentDashboard() {
        ui().openDashboard();
    }

    @Then("the deployment grid should have at least {int} rows")
    public void gridHasAtLeastRows(int min) {
        ui().assertGridHasAtLeastRows(min);
    }

    @Then("the deployment grid should show column {string}")
    public void gridShowsColumn(String colId) {
        ui().assertColumnVisible(colId);
    }

    @Then("the deployment grid should show service {string}")
    public void gridShowsService(String name) {
        ui().assertGridContainsService(name);
    }

    // ── Browser — filter ──────────────────────────────────────────────────────

    @When("I filter the deployment dashboard by {string}")
    public void filterDashboard(String text) {
        ui().filterByName(text);
    }

    @When("I clear the deployment filter")
    public void clearDeploymentFilter() {
        ui().clearFilter();
    }
}
