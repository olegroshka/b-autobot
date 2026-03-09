package com.bbot.sandbox.stepdefs;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import com.bbot.sandbox.pages.FinanceDemoPage;
import com.bbot.core.TickingCellHelper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unused")
public class FinanceDemoSteps {

    private static final String BASE_URL =
            System.getProperty("BASE_URL", "https://www.ag-grid.com/example-finance/");

    private final TestWorld world;
    private final FinanceDemoPage financePage;
    private final TickingCellHelper ticking;

    public FinanceDemoSteps(TestWorld world) {
        this.world = world;
        this.financePage = new FinanceDemoPage(world.page());
        this.ticking = new TickingCellHelper(world.page(), world.session().getConfig());
    }

    // ── Snapshot state used between When/Then steps ───────────────────────
    private String observedCellValue;
    private boolean tickReceived;

    // ─── Background ──────────────────────────────────────────────────────

    @Given("the Finance Demo page is open")
    public void theFinanceDemoPageIsOpen() {
        financePage.navigate(BASE_URL);
        financePage.assertGridLoaded();
    }

    // ─── Column visibility ────────────────────────────────────────────────

    @Then("the grid should display the {string} column")
    public void theGridShouldDisplayColumn(String colId) {
        financePage.assertColumnVisible(colId);
    }

    // ─── Ticking: observe and detect change ──────────────────────────────

    @When("I observe the price cell in row {int}")
    public void iObserveThePriceCell(int rowIndex) {
        // Capture the current value; waitForCellUpdate will detect when it differs.
        observedCellValue = financePage.getCellText("price", rowIndex);
    }

    @Then("the price cell should change its value within {int} seconds")
    public void thePriceCellShouldChangeWithin(int seconds) {
        String newValue = ticking.waitForCellUpdate(
                "price", 0,
                Duration.ofSeconds(seconds));
        assertThat(newValue)
                .as("Price cell should have updated from '%s'", observedCellValue)
                .isNotEqualTo(observedCellValue);
    }

    // ─── Ticking: range assertion ─────────────────────────────────────────

    @Then("the price of the first row should be between {double} and {double}")
    public void thePriceOfFirstRowShouldBeBetween(double min, double max) {
        ticking.assertCellValueInRange(
                "price", 0,
                min, max, Duration.ofSeconds(5));
    }

    // ─── Ticking: flash detection (generic) ──────────────────────────────────

    @When("I wait for the {string} cell in row {int} to flash")
    public void iWaitForCellToFlash(String colId, int rowIndex) {
        ticking.waitForCellFlash(
                colId, rowIndex,
                Duration.ofSeconds(10));
        tickReceived = true;
    }

    @Then("the {string} cell should have received at least one tick update")
    public void theCellShouldHaveReceivedATick(String colId) {
        assertThat(tickReceived)
                .as("Expected at least one tick flash on '%s' cell", colId)
                .isTrue();
    }

    // ─── Feed pause / resume ──────────────────────────────────────────────

    @Given("the data feed is live")
    public void theDataFeedIsLive() {
        if (!financePage.isLive()) {
            financePage.resumeFeed();
        }
    }

    @When("I pause the data feed")
    public void iPauseTheDataFeed() {
        financePage.pauseFeed();
    }

    @Then("the price cell in row {int} should not change for {int} seconds")
    public void thePriceCellShouldNotChangeFor(int rowIndex, int seconds) {
        String valueBefore = financePage.getCellText("price", rowIndex);
        // Allow time to pass without a Thread.sleep by using waitForTimeout
        // (the only permitted use — we're asserting absence of change).
        //noinspection resource — Page lifecycle is managed by PlaywrightManager
        world.page().waitForTimeout(Duration.ofSeconds(seconds).toMillis());
        String valueAfter = financePage.getCellText("price", rowIndex);
        assertThat(valueAfter)
                .as("Price should remain stable while feed is paused")
                .isEqualTo(valueBefore);
    }

    @Given("the data feed is paused")
    public void theDataFeedIsPaused() {
        if (financePage.isLive()) {
            financePage.pauseFeed();
        }
    }

    @When("I resume the data feed")
    public void iResumeTheDataFeed() {
        financePage.resumeFeed();
        observedCellValue = financePage.getCellText("price", 0);
    }

    // ─── Sorting ──────────────────────────────────────────────────────────

    @When("I click the {string} column header")
    public void iClickColumnHeader(String colId) {
        financePage.clickColumnHeader(colId);
    }

    @Then("the first row ticker should be alphabetically first")
    public void theFirstRowTickerShouldBeAlphabeticallyFirst() {
        String first  = financePage.getCellText("ticker", 0);
        String second = financePage.getCellText("ticker", 1);
        assertThat(first.compareToIgnoreCase(second))
                .as("Row 0 ticker '%s' should come before row 1 ticker '%s'", first, second)
                .isLessThanOrEqualTo(0);
    }

    // ─── Column filter ────────────────────────────────────────────────────

    @When("I filter the {string} column by {string}")
    public void iFilterColumnBy(String colId, String value) {
        financePage.applyColumnTextFilter(colId, value);
    }

    @Then("every visible row in the {string} column should contain {string}")
    public void everyVisibleRowInColumnShouldContain(String colId, String value) {
        List<String> texts = financePage.getAllVisibleCellTexts(colId);
        assertThat(texts)
                .as("Expected visible rows in '%s' column after filtering by '%s'", colId, value)
                .isNotEmpty();
        assertThat(texts)
                .as("All visible '%s' cells should contain '%s'", colId, value)
                .allSatisfy(text -> assertThat(text).containsIgnoringCase(value));
    }

    @When("I clear the filter on the {string} column")
    public void iClearFilterOnColumn(String colId) {
        financePage.clearColumnFilter(colId);
    }

    @Then("the {string} column should contain more than one distinct value")
    public void columnShouldContainMoreThanOneDistinctValue(String colId) {
        List<String> texts = financePage.getAllVisibleCellTexts(colId);
        long distinctCount = texts.stream().distinct().count();
        assertThat(distinctCount)
                .as("Expected multiple distinct values in '%s' column after clearing filter, got: %s",
                        colId, texts)
                .isGreaterThan(1);
    }

    // ─── Generic cell observation (avoids conflict with existing price steps) ──

    @When("I observe cell {string} in row {int}")
    public void iObserveCell(String colId, int rowIndex) {
        observedCellValue = financePage.getCellText(colId, rowIndex);
    }

    @Then("cell {string} in row {int} should change within {int} seconds")
    public void cellInRowShouldChangeWithin(String colId, int rowIndex, int seconds) {
        String newValue = ticking.waitForCellUpdate(
                colId, rowIndex,
                Duration.ofSeconds(seconds));
        assertThat(newValue)
                .as("Cell [col='%s', row=%d] should have updated from '%s'", colId, rowIndex, observedCellValue)
                .isNotEqualTo(observedCellValue);
    }
}
