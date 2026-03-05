package stepdefs;

import com.microsoft.playwright.Page;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import utils.MockBlotterServer;
import utils.PlaywrightManager;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for BondBlotter.feature.
 *
 * <p>M0 scope: page navigation + title assertion.
 * M1+ scope: grid column/row assertions will be added here as milestones progress.
 */
public class BondBlotterSteps {

    private final Page page = PlaywrightManager.getPage();

    // ── Navigation ────────────────────────────────────────────────────────────

    @Given("the PT-Blotter is open")
    public void thePtBlotterIsOpen() {
        page.navigate(MockBlotterServer.getBlotterUrl());
        // Wait for the DOM to be ready — DOMCONTENTLOADED is faster than LOAD
        // and sufficient for the static pre-built page.
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
    }

    // ── M0: Page-level assertions ─────────────────────────────────────────────

    @Then("the page title should contain {string}")
    public void thePageTitleShouldContain(String expected) {
        assertThat(page).hasTitle(java.util.regex.Pattern.compile(".*" + expected + ".*"));
    }

    // ── M1: Grid structure assertions ─────────────────────────────────────────

    @Then("the grid should display column {string}")
    public void theGridShouldDisplayColumn(String colId) {
        assertThat(page.locator(".ag-header-cell[col-id='" + colId + "']")).isVisible();
    }

    @Then("the grid should have at least {int} rows")
    public void theGridShouldHaveAtLeastRows(int minRows) {
        // Row index is 0-based; checking row (minRows-1) means at least minRows rows exist.
        assertThat(page.locator(
                ".ag-center-cols-container [row-index='" + (minRows - 1) + "']")).isVisible();
    }

    @Then("the row with ISIN {string} should have status {string}")
    public void theRowWithIsinShouldHaveStatus(String isin, String expectedStatus) {
        // Locate the row by finding the ISIN cell text, then read the status cell
        // from the same row-index using the JS probe.
        Object rowIndexResult = page.evaluate(
                "isin => window.agGridProbes.dom.findRowIndexByText('isin', isin)", isin);
        int rowIndex = rowIndexResult instanceof Number n ? n.intValue() : -1;
        assertThat(rowIndex)
                .as("Row with ISIN '%s' not found in visible viewport", isin)
                .isGreaterThanOrEqualTo(0);

        String statusText = page.locator(
                ".ag-center-cols-container [row-index='" + rowIndex + "'] [col-id='status']")
                .textContent().trim();
        assertThat(statusText)
                .as("Status for ISIN '%s'", isin)
                .isEqualTo(expectedStatus);
    }
}
