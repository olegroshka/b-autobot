package stepdefs;

import com.bbot.core.data.TestDataConfig;
import com.bbot.core.PlaywrightManager;
import com.bbot.core.registry.BBotRegistry;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import utils.PtBlotterDsl;

import java.time.Duration;

/**
 * Step definitions for PT-Blotter scenarios.
 *
 * <p>Every method delegates entirely to {@link PtBlotterDsl} — no Playwright calls
 * here. This class contains only the Gherkin-to-Java wiring.
 *
 * <h2>Template customisation points</h2>
 * <ul>
 *   <li>Rename this class to match your application (e.g. {@code TradingDeskSteps}).</li>
 *   <li>Replace the DSL type with your own DSL class.</li>
 *   <li>Add {@code @Given} / {@code @When} / {@code @Then} methods for each
 *       Gherkin step in your feature files — delegate entirely to the DSL.</li>
 * </ul>
 */
public class BlotterSteps {

    // DSL is instantiated fresh for every scenario via BBotRegistry.
    private final PtBlotterDsl blotter =
            BBotRegistry.dsl("blotter", PlaywrightManager.getPage(), PtBlotterDsl.class);

    private TestDataConfig testData() {
        return BBotRegistry.getConfig().getTestData();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @Given("the PT-Blotter is open")
    public void ptBlotterIsOpen() { blotter.openBlotter(); }

    @Given("the PT-Blotter is open as {string}")
    public void ptBlotterIsOpenAs(String user) { blotter.openBlotter(user); }

    // ── Page-level ────────────────────────────────────────────────────────────

    @Then("the page title should contain {string}")
    public void pageTitleShouldContain(String expected) {
        blotter.assertTitleContains(expected);
    }

    @Then("the blotter grid should be visible")
    public void blotterGridShouldBeVisible() { blotter.assertGridRendered(); }

    // ── Grid schema ───────────────────────────────────────────────────────────

    @Then("the grid should display column {string}")
    public void gridShouldDisplayColumn(String colId) {
        blotter.assertGridHasColumn(colId);
    }

    @Then("the grid should have at least {int} rows")
    public void gridShouldHaveAtLeastRows(int minRows) {
        blotter.assertGridHasMinRows(minRows);
    }

    // ── Row assertions ────────────────────────────────────────────────────────

    @Then("the row with ISIN {string} should have status {string}")
    public void rowShouldHaveStatus(String isin, String status) {
        blotter.assertRowStatus(isin, status);
    }

    @Then("the {string} for ISIN {string} should be a numeric value")
    public void cellShouldBeNumeric(String colId, String isin) {
        blotter.assertCellNumeric(colId, isin);
    }

    @Then("the {string} for ISIN {string} should be blank")
    public void cellShouldBeBlank(String colId, String isin) {
        blotter.assertCellBlank(colId, isin);
    }

    // ── Ticking cells ─────────────────────────────────────────────────────────

    @When("I wait up to {int} seconds for the {string} cell in row {int} to change value")
    public void waitForCellToChange(int seconds, String colId, int rowIndex) {
        blotter.waitForCellToChange(colId, rowIndex, Duration.ofSeconds(seconds));
    }

    @Then("the {string} cell in row {int} should match the pattern {string}")
    public void cellShouldMatchPattern(String colId, int rowIndex, String pattern) {
        blotter.assertCellMatchesPattern(colId, rowIndex, pattern);
    }

    // ── Row selection ─────────────────────────────────────────────────────────

    @When("I select the row with ISIN {string}")
    public void selectRowByIsin(String isin) { blotter.selectRowByIsin(isin); }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    @And("I set the toolbar source {string} side {string} markup {string} units {string}")
    public void setToolbar(String source, String side, String markup, String units) {
        blotter.setToolbar(source, side, markup, units);
    }

    @And("I press APPLY")  public void pressApply()    { blotter.pressApply(); }
    @And("I press SEND")   public void pressSend()     { blotter.pressSend(); }
    @And("I press RELEASE PT") public void pressReleasePt() { blotter.pressReleasePt(); }

    // ── Access control ────────────────────────────────────────────────────────

    @Then("the RELEASE PT button should be disabled")
    public void releasePtShouldBeDisabled() { blotter.assertReleasePtDisabled(); }

    @Then("the RELEASE PT button should be enabled")
    public void releasePtShouldBeEnabled()  { blotter.assertReleasePtEnabled(); }

    // ── Named-user navigation ─────────────────────────────────────────────────

    @Given("the PT-Blotter is open as the trader")
    public void ptBlotterIsOpenAsTrader() { blotter.openBlotter(testData().getUser("trader")); }

    @Given("the PT-Blotter is open as the admin")
    public void ptBlotterIsOpenAsAdmin()  { blotter.openBlotter(testData().getUser("admin")); }

    // ── Bond-list ISIN step variants ──────────────────────────────────────────

    @When("I select the row with ISIN from {string} field {string}")
    public void selectRowByBondRef(String bondList, String field) {
        blotter.selectRowByIsin(testData().resolveBondRef(bondList, field));
    }

    @Then("the row with ISIN from {string} field {string} should have status {string}")
    public void rowByBondRefShouldHaveStatus(String bondList, String field, String status) {
        blotter.assertRowStatus(testData().resolveBondRef(bondList, field), status);
    }

    @Then("the {string} for ISIN from {string} field {string} should be a numeric value")
    public void cellByBondRefShouldBeNumeric(String colId, String bondList, String field) {
        blotter.assertCellNumeric(colId, testData().resolveBondRef(bondList, field));
    }

    @Then("the {string} for ISIN from {string} field {string} should be blank")
    public void cellByBondRefShouldBeBlank(String colId, String bondList, String field) {
        blotter.assertCellBlank(colId, testData().resolveBondRef(bondList, field));
    }
}
