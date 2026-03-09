package com.bbot.sandbox.stepdefs;

import com.bbot.core.data.TestDataConfig;
import com.microsoft.playwright.APIResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import com.bbot.sandbox.utils.BlotterDsl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for BondBlotter.feature.
 *
 * <p>This class is intentionally a thin delegation layer — every step
 * delegates to {@link BlotterDsl}, which owns all Playwright interactions.
 * No raw {@code page.*} calls should appear here (M7 DSL contract).
 *
 * <p>Milestone scope:
 * <ul>
 *   <li>M0 — page navigation + title assertion</li>
 *   <li>M1 — grid column / row structure</li>
 *   <li>M2 — ticking cell assertions (shared with FinanceDemoSteps for flash)</li>
 *   <li>M3 — REST inquiry ingestion</li>
 *   <li>M4 — Toolbar: ref source / ref side / markup ± / units / APPLY</li>
 *   <li>M5 — SEND: status → QUOTED, quotedPrice/quotedSpread snapshot</li>
 *   <li>M6 — multi-row APPLY / SEND</li>
 *   <li>M7 — end-to-end DSL re-quote scenario (no new steps needed)</li>
 * </ul>
 */
public class BondBlotterSteps {

    private final TestWorld world;
    private final BlotterDsl dsl;

    public BondBlotterSteps(TestWorld world) {
        this.world = world;
        this.dsl = world.session().dsl("blotter", world.page(), BlotterDsl.class);
    }

    private TestDataConfig testData() {
        return world.session().getConfig().getTestData();
    }

    // Shared state between When/Then steps within a scenario
    private APIResponse lastApiResponse;

    // ── M0: Navigation ────────────────────────────────────────────────────────

    @Given("the PT-Blotter is open")
    public void thePtBlotterIsOpen() {
        dsl.openBlotter();
    }

    @Then("the page title should contain {string}")
    public void thePageTitleShouldContain(String expected) {
        dsl.assertTitle(expected);
    }

    // ── M1: Grid structure ────────────────────────────────────────────────────

    @Then("the grid should display column {string}")
    public void theGridShouldDisplayColumn(String colId) {
        dsl.assertColumnVisible(colId);
    }

    @Then("the grid should have at least {int} rows")
    public void theGridShouldHaveAtLeastRows(int minRows) {
        dsl.assertAtLeastRows(minRows);
    }

    @Then("the row with ISIN {string} should have status {string}")
    public void theRowWithIsinShouldHaveStatus(String isin, String expectedStatus) {
        dsl.assertStatus(isin, expectedStatus);
    }

    // ── M2: Ticking assertions ────────────────────────────────────────────────

    // NOTE: "I wait for the {string} cell in row {int} to flash" is defined in
    // FinanceDemoSteps and shared — do not re-declare here.

    @When("I wait up to {int} seconds for the {string} cell in row {int} to change value")
    public void iWaitForCellToChangeValue(int seconds, String colId, int rowIndex) {
        dsl.waitForCellUpdate(colId, rowIndex, seconds);
    }

    @Then("the {string} cell in row {int} should match the pattern {string}")
    public void theCellShouldMatchPattern(String colId, int rowIndex, String pattern) {
        dsl.assertCellMatchesPattern(colId, rowIndex, pattern);
    }

    @Then("the {string} cell in row {int} should have received at least one tick update")
    public void theCellShouldHaveReceivedAtLeastOneTick(String colId, int rowIndex) {
        dsl.assertCellNotBlankByIndex(colId, rowIndex);
    }

    @Then("within {int} seconds the {string} cell in row {int} should change value")
    public void withinSecondsTheCellShouldChangeValue(int seconds, String colId, int rowIndex) {
        dsl.waitForCellValueChange(colId, rowIndex, seconds);
    }

    // ── M3: REST inquiry API ──────────────────────────────────────────────────

    @When("a new inquiry is submitted for ISIN {string} notional {string} side {string} client {string}")
    public void aNewInquiryIsSubmitted(String isin, String notional, String side, String client) {
        lastApiResponse = dsl.submitInquiry(isin, notional, side, client);
    }

    /** Overload for the unknown-ISIN scenario (no side/client). */
    @When("a new inquiry is submitted for ISIN {string}")
    public void aNewInquiryIsSubmittedForIsin(String isin) {
        lastApiResponse = dsl.submitInquiry(isin);
    }

    @Then("the blotter API response status should be {int}")
    public void theBlotterApiResponseStatusShouldBe(int expectedStatus) {
        dsl.assertApiStatus(lastApiResponse, expectedStatus);
    }

    @Then("the response should contain a non-blank {string}")
    public void theResponseShouldContainANonBlank(String fieldName) {
        dsl.assertResponseContainsField(lastApiResponse, fieldName);
    }

    // ── M4: Toolbar + APPLY ───────────────────────────────────────────────────

    @When("I select the row with ISIN {string}")
    public void iSelectTheRowWithIsin(String isin) {
        dsl.selectRowByIsin(isin);
    }

    /**
     * Configures all four toolbar controls in one step.
     * Example: I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
     */
    @When("I set the toolbar ref source {string} ref side {string} markup {string} units {string}")
    public void iSetToolbar(String refSource, String refSide, String markup, String units) {
        dsl.setToolbar(refSource, refSide, markup, units);
    }

    @When("I press APPLY")
    public void iPressApply() {
        dsl.pressApply();
    }

    @When("I press the markup plus button")
    public void iPressMarkupPlus() {
        dsl.pressMarkupPlus();
    }

    @When("I press the markup minus button")
    public void iPressMarkupMinus() {
        dsl.pressMarkupMinus();
    }

    @Then("the markup input should show a positive value")
    public void markupInputShouldBePositive() {
        assertThat(dsl.readMarkupValue())
                .as("Markup input value after pressing +")
                .isGreaterThan(0);
    }

    @Then("the markup input should show a negative value")
    public void markupInputShouldBeNegative() {
        assertThat(dsl.readMarkupValue())
                .as("Markup input value after pressing −")
                .isLessThan(0);
    }

    // ── M4/M5: Applied cell assertions ────────────────────────────────────────

    @Then("the {string} for ISIN {string} should be a numeric value")
    public void theFieldForIsinShouldBeNumeric(String colId, String isin) {
        dsl.assertCellNumeric(colId, isin);
    }

    @Then("the {string} for ISIN {string} should be blank")
    public void theFieldForIsinShouldBeBlank(String colId, String isin) {
        dsl.assertCellBlank(colId, isin);
    }

    // ── M5: SEND ──────────────────────────────────────────────────────────────

    @When("I press SEND")
    public void iPressSend() {
        dsl.pressSend();
    }

    // ── M8: RELEASE PT access control + workflow ──────────────────────────────

    @Given("the PT-Blotter is open as user {string}")
    public void openBlotterAsUser(String user) {
        dsl.openBlotter(user);
    }

    @Given("the PT-Blotter is open as the trader")
    public void openBlotterAsTrader() {
        dsl.openBlotter(testData().getUser("trader"));
    }

    @Given("the PT-Blotter is open as the admin")
    public void openBlotterAsAdmin() {
        dsl.openBlotter(testData().getUser("admin"));
    }

    // ── Bond-list ISIN step variants ──────────────────────────────────────────
    // Use these instead of hardcoding ISINs in feature files.
    // The ISIN is resolved from b-bot.test-data.bond-lists at runtime.

    @When("I select the row with ISIN from {string} field {string}")
    public void selectRowByBondRef(String bondList, String field) {
        dsl.selectRowByIsin(testData().resolveBondRef(bondList, field));
    }

    @Then("the row with ISIN from {string} field {string} should have status {string}")
    public void rowByBondRefShouldHaveStatus(String bondList, String field, String status) {
        dsl.assertStatus(testData().resolveBondRef(bondList, field), status);
    }

    @Then("the {string} for ISIN from {string} field {string} should be a numeric value")
    public void cellByBondRefShouldBeNumeric(String colId, String bondList, String field) {
        dsl.assertCellNumeric(colId, testData().resolveBondRef(bondList, field));
    }

    @Then("the {string} for ISIN from {string} field {string} should be blank")
    public void cellByBondRefShouldBeBlank(String colId, String bondList, String field) {
        dsl.assertCellBlank(colId, testData().resolveBondRef(bondList, field));
    }

    @When("a new inquiry is submitted for ISIN from {string} field {string} notional {string} side {string} client {string}")
    public void submitInquiryByBondRef(String bondList, String field,
                                        String notional, String side, String client) {
        lastApiResponse = dsl.submitInquiry(
                testData().resolveBondRef(bondList, field), notional, side, client);
    }

    @When("a new inquiry is submitted for ISIN from {string} field {string}")
    public void submitInquiryByBondRefSimple(String bondList, String field) {
        lastApiResponse = dsl.submitInquiry(testData().resolveBondRef(bondList, field));
    }

    @Then("the RELEASE PT button should be disabled")
    public void releasePtDisabled() {
        dsl.assertReleasePtDisabled();
    }

    @Then("the RELEASE PT button should be enabled")
    public void releasePtEnabled() {
        dsl.assertReleasePtEnabled();
    }

    @When("I press RELEASE PT")
    public void pressReleasePt() {
        dsl.pressReleasePt();
    }
}
