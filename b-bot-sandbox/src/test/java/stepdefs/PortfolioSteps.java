package com.bbot.sandbox.stepdefs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.WaitUntilState;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import com.bbot.sandbox.model.Trade;
import com.bbot.sandbox.model.TradePortfolio;
import com.bbot.core.GridHarness;
import com.bbot.core.NumericComparator;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for {@code PortfolioRegression.feature}.
 *
 * <h2>Hybrid Data Pattern</h2>
 * <ol>
 *   <li>Use Playwright's {@link APIRequestContext} ({@code page.request()}) to POST a
 *       {@link TradePortfolio} to {@link com.bbot.sandbox.utils.MockBlotterServer MockBlotterServer}.</li>
 *   <li>Capture the server-assigned {@code portfolio_id} and the full response
 *       as a {@link JsonNode} for later field lookups.</li>
 *   <li>Render an AG Grid blotter page in the same Playwright {@link Page} via
 *       {@code page.setContent()} — the grid is populated with the API response data.</li>
 *   <li>Use {@link GridHarness} to find and assert on grid cells even if virtualised.</li>
 *   <li>Use {@link NumericComparator} to compare UI text vs JSON values with
 *       tolerance for trailing zeros and thousand separators.</li>
 * </ol>
 */
public class PortfolioSteps {

    // ── Shared infrastructure ─────────────────────────────────────────────────

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GRID_ROLE_SELECTOR = "[role='grid']";
    private static final int GRID_MOUNT_TIMEOUT_MS = 20_000;


    private final TestWorld world;

    public PortfolioSteps(TestWorld world) {
        this.world = world;
    }

    // ── Scenario-scoped state (new instance per scenario) ─────────────────────

    private APIResponse    apiResponse;
    private String         returnedPortfolioId;
    private JsonNode       apiResponseNode;   // full parsed response for field lookups
    private Locator        foundCell;

    // ═════════════════════════════════════════════════════════════════════════
    // REST API steps
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Builds a Fixed Income portfolio payload and POSTs it via Playwright's
     * {@link APIRequestContext} to the blotter's {@code /submit} endpoint.
     *
     * <p>The trader ID is resolved from {@code b-bot.test-data.users} by role name.
     * The submit URL is derived from {@code b-bot.apps.blotter.apiBase} — no URLs
     * in feature files.
     */
    @SuppressWarnings("resource")   // Page lifecycle is managed by PlaywrightManager
    @Given("the user from role {string} submits a portfolio via REST API")
    public void userRoleSubmitsPortfolioViaRestApi(String role) throws Exception {
        String traderId = world.session().getConfig().getTestData().getUser(role);
        String endpoint = world.session().getConfig().getAppApiBase("blotter") + "/submit";

        // ── 1. Build payload ──────────────────────────────────────────────────
        TradePortfolio submittedPortfolio = TradePortfolio.builder()
                .traderId(traderId)
                .submittedAt(Instant.now().toString())
                .currency("USD")
                .status("PENDING")
                .blotterId("BL-FI-001")
                .desk("FIXED_INCOME")
                .totalFaceValue(6_000_000.00)
                .totalMarketValue(5_937_500.00)
                .accruedInterest(12_345.67)
                .trades(List.of(
                        Trade.builder()
                                .tradeId("TR-" + System.currentTimeMillis() + "-1")
                                .isin("US912828ZL70")
                                .securityName("US Treasury 4.25% 2027")
                                .assetClass("GOVERNMENT_BOND")
                                .side("BUY")
                                .quantity(5_000_000)
                                .price(98.75).yield(4.52).couponRate(4.25)
                                .maturityDate("2027-03-15")
                                .faceValue(5_000_000.00).marketValue(4_937_500.00)
                                .currency("USD")
                                .settlementDate(LocalDate.now().plusDays(2).toString())
                                .build(),
                        Trade.builder()
                                .tradeId("TR-" + System.currentTimeMillis() + "-2")
                                .isin("US38141GXZ20").cusip("38141GXZ2")
                                .securityName("Goldman Sachs 3.50% 2026")
                                .assetClass("CORPORATE_BOND")
                                .side("SELL")
                                .quantity(1_000_000)
                                .price(97.50).yield(4.85).couponRate(3.50)
                                .maturityDate("2026-01-15")
                                .faceValue(1_000_000.00).marketValue(975_000.00)
                                .currency("USD")
                                .settlementDate(LocalDate.now().plusDays(2).toString())
                                .build()
                ))
                .build();

        // ── 2. POST via Playwright APIRequestContext ───────────────────────────
        //
        // page.request() returns an APIRequestContext bound to the browser context,
        // sharing the same cookie jar — important for SSO-protected blotters.
        Page page = world.page();
        APIRequestContext ctx = page.request();

        apiResponse = ctx.post(endpoint,
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Accept", "application/json")
                        .setData(MAPPER.writeValueAsString(submittedPortfolio)));

        // ── 3. Parse response ─────────────────────────────────────────────────
        if (apiResponse.status() == 200 || apiResponse.status() == 201) {
            apiResponseNode     = MAPPER.readTree(apiResponse.body());
            returnedPortfolioId = apiResponseNode.path("portfolio_id").asText(null);
        }
    }

    @Then("the API response status should be {int}")
    public void theApiResponseStatusShouldBe(int expected) {
        assertThat(apiResponse.status())
                .as("HTTP status: expected %d, got %d. Body: %s",
                    expected, apiResponse.status(), apiResponse.text())
                .isEqualTo(expected);
    }

    @And("the API response should contain a non-blank {string}")
    public void theApiResponseShouldContainNonBlank(String fieldName) {
        String value;
        if ("portfolio_id".equals(fieldName)) {
            value = returnedPortfolioId;
        } else {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        assertThat(value)
                .as("Response field '%s' must not be blank", fieldName)
                .isNotBlank();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Blotter / UI steps
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Navigates to the {@code webUrl} of the named app and waits for the AG Grid to mount.
     * Used for the external demo scenarios (e.g. app {@code "finance-demo"}).
     */
    @SuppressWarnings("resource")   // Page lifecycle is managed by PlaywrightManager
    @And("the blotter at app {string} is open")
    public void theBlotterIsOpen(String appName) {
        String url = world.session().getConfig().getAppWebUrl(appName);
        Page page = world.page();
        page.navigate(url);
        page.locator(GRID_ROLE_SELECTOR)
                .waitFor(new Locator.WaitForOptions().setTimeout(GRID_MOUNT_TIMEOUT_MS));
    }

    /**
     * Injects a self-contained AG Grid blotter page into the current Playwright
     * page using {@code page.setContent()}.  The grid is populated with the data
     * returned by the earlier API call.
     *
     * <p>This approach avoids any dependency on an external URL: WireMock provides
     * the API, Playwright renders the grid, and the test is fully deterministic.
     *
     * <p>The AG Grid library is loaded from jsDelivr CDN.  The page waits for
     * {@code window.gridApi} to be defined (proving the grid has initialised) before
     * returning control to the test.
     */
    @SuppressWarnings("resource")   // Page lifecycle is managed by PlaywrightManager
    @And("the blotter is populated with the API response")
    public void theBlotterIsPopulatedWithApiResponse() throws Exception {
        assertThat(apiResponseNode)
                .as("API response must have been received before populating the blotter")
                .isNotNull();

        // Build camelCase row data from the snake_case API response
        String rowDataJson = MAPPER.writeValueAsString(List.of(buildGridRow(apiResponseNode)));

        Page page = world.page();

        // Render the CDN-free mock blotter; DOMCONTENTLOADED is sufficient
        // because the mock DOM is built by an inline script with no external resources.
        page.setContent(blotterHtml(rowDataJson),
                new Page.SetContentOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(10_000));

        // Wait for the AG Grid JavaScript to finish initialising and set window.gridApi
        page.waitForFunction("() => typeof window.gridApi !== 'undefined'",
                null,
                new Page.WaitForFunctionOptions().setTimeout(15_000));
    }

    @SuppressWarnings("resource")   // Page lifecycle is managed by PlaywrightManager
    @Then("the AG Grid should display the {string} column")
    public void theAgGridShouldDisplayColumn(String colId) {
        // LocatorAssertions.isVisible() has built-in retry; no options needed here.
        assertThat(world.page()
                .locator(String.format(".ag-header-cell[col-id='%s']", colId)))
                .isVisible();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Hybrid assertion — Portfolio ID in first row
    // ═════════════════════════════════════════════════════════════════════════

    @Then("the AG Grid should display the {string} from the API response in the first row")
    public void theAgGridShouldDisplayFieldFromApiResponse(String fieldLabel) {
        String expectedValue = resolveLabel(fieldLabel);
        assertThat(expectedValue)
                .as("API must have returned '%s' before the UI can be asserted", fieldLabel)
                .isNotBlank();

        GridHarness harness = new GridHarness(world.page(), world.session().getConfig());
        foundCell = harness.findRowByCellValue("portfolioId", expectedValue, Duration.ofSeconds(10));

        assertThat(foundCell).isVisible();
        assertThat(foundCell).hasText(expectedValue);

        int rowIdx = harness.getRowIndex(foundCell);
        assertThat(rowIdx)
                .as("Portfolio ID '%s' should appear in row 0 (newest first)", expectedValue)
                .isEqualTo(0);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // NEW ── verify cell {string} in row {string} matches the API field {string}
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scrapes a grid cell's text, looks up the expected value from the stored API
     * response JSON, and asserts they are numerically/semantically equivalent.
     *
     * <p>The {@code apiFieldPath} supports simple field names ({@code "portfolio_id"})
     * and dot/bracket paths ({@code "trades[0].price"}).
     *
     * <p>Numeric comparison uses {@link NumericComparator#assertEquivalent} which
     * strips thousand separators and currency symbols then compares as
     * {@link java.math.BigDecimal}, so {@code "5,937,500.0"} equals {@code "5937500"}.
     *
     * @param colId        AG Grid {@code col-id} of the cell to scrape
     * @param rowStr       Row index as a string (e.g. {@code "0"})
     * @param apiFieldPath JSON path into the API response (dot/bracket notation)
     */
    @Then("verify cell {string} in row {string} matches the API field {string}")
    public void verifyCellMatchesApiField(String colId, String rowStr, String apiFieldPath) {

        assertThat(apiResponseNode)
                .as("API response not available — run the Given step first")
                .isNotNull();

        int rowIndex = Integer.parseInt(rowStr);

        // ── a) Scrape the UI value using GridHarness ──────────────────────────
        String uiValue = new GridHarness(world.page(), world.session().getConfig())
                .getCellText(colId, rowIndex);

        // ── b) Extract the expected value from the stored API JSON response ───
        String apiValue = NumericComparator.extractFieldValue(apiResponseNode, apiFieldPath);

        // ── c) Compare with numeric precision (handles 100.00 vs 100, etc.) ──
        NumericComparator.assertEquivalent(uiValue, apiValue, colId, rowIndex, apiFieldPath);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GridHarness demonstration steps
    // ═════════════════════════════════════════════════════════════════════════

    @When("I search the grid for ticker {string}")
    public void iSearchTheGridForTicker(String ticker) {
        GridHarness harness = new GridHarness(world.page(), world.session().getConfig());
        foundCell = harness.findRowByCellValue("ticker", ticker, Duration.ofSeconds(15));
    }

    @Then("the matching row should be visible and the {string} cell should contain {string}")
    public void theMatchingRowShouldBeVisible(String colId, String expected) {
        // Cast to Object so AssertJ's assertThat resolves (not PlaywrightAssertions)
        assertThat((Object) foundCell).isNotNull();
        assertThat(foundCell).isVisible();
        GridHarness harness = new GridHarness(world.page(), world.session().getConfig());
        String actual = "ticker".equals(colId)
                ? foundCell.textContent().trim()
                : harness.getSiblingCellText(foundCell, colId);
        // The ticker cell has a custom renderer that may include company name alongside
        // the ticker symbol — use contains rather than exact-equals.
        assertThat(actual)
                .as("Cell [col-id='%s'] in found row should contain '%s'", colId, expected)
                .contains(expected);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═════════════════════════════════════════════════════════════════════════

    private String resolveLabel(String label) {
        if ("Portfolio ID".equals(label)) {
            return returnedPortfolioId;
        }
        throw new IllegalArgumentException("Unknown label: " + label);
    }

    /**
     * Maps the snake_case JSON keys from the API response to the camelCase
     * field names used as AG Grid {@code col-id} values in the blotter page.
     */
    private static Map<String, Object> buildGridRow(JsonNode node) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("portfolioId",      node.path("portfolio_id").asText(""));
        row.put("traderId",         node.path("trader_id").asText(""));
        row.put("status",           node.path("status").asText(""));
        row.put("currency",         node.path("currency").asText(""));
        row.put("desk",             node.path("desk").asText(""));
        row.put("blotterId",        node.path("blotter_id").asText(""));
        row.put("totalMarketValue", node.path("total_market_value").asDouble(0.0));
        row.put("totalFaceValue",   node.path("total_face_value").asDouble(0.0));
        row.put("accruedInterest",  node.path("accrued_interest").asDouble(0.0));
        return row;
    }

    /**
     * Builds a self-contained HTML page that mimics AG Grid's DOM structure
     * without loading any external CDN assets.
     *
     * <p>The generated DOM matches the selectors used by {@link GridHarness}
     * and {@link com.bbot.core.TickingCellHelper}:
     * <ul>
     *   <li>{@code .ag-center-cols-container} — virtualised row container</li>
     *   <li>{@code [row-index='N']} — row wrapper</li>
     *   <li>{@code [col-id='X']} — individual cell</li>
     *   <li>{@code window.gridApi} — mock with {@code forEachNode} / {@code ensureIndexVisible}</li>
     * </ul>
     */
    private static String blotterHtml(String rowDataJson) {
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8">
                <style>
                  .ag-root-wrapper { border: 1px solid #ccc; font-family: sans-serif; }
                  .ag-center-cols-container > div { display: flex; border-bottom: 1px solid #eee; }
                  [col-id] { padding: 6px 12px; min-width: 120px; border-right: 1px solid #eee; font-size: 13px; }
                </style>
                </head>
                <body>
                <h3 style="margin:8px 0">Fixed Income Blotter</h3>
                <div role="grid" class="ag-root-wrapper">
                  <div class="ag-center-cols-container" id="rows"></div>
                </div>
                <script>
                  (function () {
                    var rowData = %s;
                    var container = document.getElementById('rows');
                    rowData.forEach(function (data, i) {
                      var row = document.createElement('div');
                      row.setAttribute('row-index', i);
                      Object.keys(data).forEach(function (col) {
                        var cell = document.createElement('div');
                        cell.setAttribute('col-id', col);
                        cell.textContent = data[col];
                        row.appendChild(cell);
                      });
                      container.appendChild(row);
                    });
                    window.gridApi = {
                      forEachNode: function (cb) {
                        rowData.forEach(function (data, i) { cb({ data: data, rowIndex: i }); });
                      },
                      ensureIndexVisible: function () {}
                    };
                  }());
                </script>
                </body></html>
                """.formatted(rowDataJson);
    }
}

