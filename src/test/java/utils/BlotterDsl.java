package utils;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.RequestOptions;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * DSL (Domain-Specific Language) wrapper for the PT-Blotter UI.
 *
 * <p>Encapsulates <em>all</em> direct Playwright interactions so that
 * {@code BondBlotterSteps} contains only Cucumber annotations and delegation
 * — zero raw Playwright calls.
 *
 * <p>Follows the Page Object Model guideline in CLAUDE.md: locator
 * definitions live here; assertions are expressed here and called from
 * step definitions.
 */
public final class BlotterDsl {

    private final Page page;

    public BlotterDsl(Page page) {
        this.page = page;
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    public void openBlotter() {
        page.navigate(MockBlotterServer.getBlotterUrl());
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
    }

    // ── M0 ─────────────────────────────────────────────────────────────────────

    public void assertTitle(String expected) {
        assertThat(page).hasTitle(Pattern.compile(".*" + Pattern.quote(expected) + ".*"));
    }

    // ── M1 — Grid structure ────────────────────────────────────────────────────

    public void assertColumnVisible(String colId) {
        assertThat(headerCell(colId)).isVisible();
    }

    public void assertAtLeastRows(int minRows) {
        // Row indices are 0-based; checking row (minRows-1) proves ≥ minRows rows exist.
        assertThat(page.locator(
                ".ag-center-cols-container [row-index='" + (minRows - 1) + "']")).isVisible();
    }

    // ── Row lookup ─────────────────────────────────────────────────────────────

    /**
     * Finds the DOM row-index for the given ISIN via the agGridProbes JS bundle.
     * Asserts the row is visible and returns its index (≥ 0).
     */
    public int findRowIndex(String isin) {
        Object result = page.evaluate(
                "isin => window.agGridProbes.dom.findRowIndexByText('isin', isin)", isin);
        int index = result instanceof Number n ? n.intValue() : -1;
        assertThat(index).as("Row with ISIN '%s' not found in grid", isin).isGreaterThanOrEqualTo(0);
        return index;
    }

    /** Returns a locator for a data cell in the centre column container. */
    public Locator dataCell(String colId, int rowIndex) {
        return page.locator(
                ".ag-center-cols-container [row-index='" + rowIndex + "'] [col-id='" + colId + "']");
    }

    private Locator headerCell(String colId) {
        return page.locator(".ag-header-cell[col-id='" + colId + "']");
    }

    // ── M1 — Row status assertion ──────────────────────────────────────────────

    /** Locates the row by ISIN, then asserts the status cell matches expectedStatus (auto-retrying). */
    public void assertStatus(String isin, String expectedStatus) {
        int rowIndex = findRowIndex(isin);
        // status col is in the centre container; auto-retry for SEND async updates
        assertThat(dataCell("status", rowIndex)).hasText(expectedStatus);
    }

    // ── M2 — Ticking ──────────────────────────────────────────────────────────

    @SuppressWarnings("UnusedReturnValue")
    public String waitForCellUpdate(String colId, int rowIndex, int seconds) {
        return TickingCellHelper.waitForCellUpdate(
                page, colId, rowIndex, Duration.ofSeconds(seconds));
    }

    public void assertCellMatchesPattern(String colId, int rowIndex, String pattern) {
        String text = dataCell(colId, rowIndex).textContent().trim();
        assertThat(text)
                .as("Cell [col-id='%s'][row-index=%d]", colId, rowIndex)
                .matches(Pattern.compile(pattern));
    }

    public void assertCellNotBlankByIndex(String colId, int rowIndex) {
        String text = dataCell(colId, rowIndex).textContent().trim();
        assertThat(text).as("Cell [col-id='%s'][row-index=%d]", colId, rowIndex).isNotBlank();
    }

    public void waitForCellValueChange(String colId, int rowIndex, int seconds) {
        TickingCellHelper.waitForCellUpdate(page, colId, rowIndex, Duration.ofSeconds(seconds));
    }

    // ── M3 — REST API ──────────────────────────────────────────────────────────

    public APIResponse submitInquiry(String isin, String notional, String side, String client) {
        APIRequestContext req = page.context().request();
        return req.post(
                MockBlotterServer.getBaseUrl() + "/api/inquiry",
                RequestOptions.create().setData(Map.of(
                        "isin",     isin,
                        "notional", Long.parseLong(notional),
                        "side",     side,
                        "client",   client)));
    }

    public APIResponse submitInquiry(String isin) {
        APIRequestContext req = page.context().request();
        return req.post(
                MockBlotterServer.getBaseUrl() + "/api/inquiry",
                RequestOptions.create().setData(Map.of("isin", isin)));
    }

    public void assertApiStatus(APIResponse response, int expectedStatus) {
        assertThat(response.status())
                .as("API HTTP status")
                .isEqualTo(expectedStatus);
    }

    public void assertResponseContainsField(APIResponse response, String fieldName) {
        String body = response.text();
        assertThat(body)
                .as("Response body")
                .contains("\"" + fieldName + "\"")
                .doesNotContain("\"" + fieldName + "\":\"\"")
                .doesNotContain("\"" + fieldName + "\":null");
    }

    // ── M4 — Row selection ─────────────────────────────────────────────────────

    /**
     * Selects the row identified by {@code isin} by clicking its checkbox.
     * The 'select' and 'isin' columns are left-pinned; the checkbox is inside
     * {@code .ag-pinned-left-cols-container}.
     */
    public void selectRowByIsin(String isin) {
        int rowIndex = findRowIndex(isin);
        // Use the pinned left container for the checkbox column
        page.locator(".ag-pinned-left-cols-container [row-index='" + rowIndex
                + "'] [col-id='select'] input[type='checkbox']").click();
    }

    // ── M4 — Toolbar interaction ───────────────────────────────────────────────

    /**
     * Sets all four toolbar controls.
     *
     * @param refSource  TW | CP+ | CBBT
     * @param refSide    Bid | Ask | Mid
     * @param markup     numeric string, e.g. "0", "-0.25", "1.5"
     * @param units      c | bp
     */
    public void setToolbar(String refSource, String refSide, String markup, String units) {
        page.locator("select[aria-label='Ref Source']").selectOption(refSource);
        page.locator("select[aria-label='Ref Side']").selectOption(refSide);
        // Clear then fill — avoids residual digits from a previous value
        Locator markupInput = page.locator("input[aria-label='Markup Value']");
        markupInput.clear();
        markupInput.fill(markup);
        // Units toggle: click the button whose aria-label matches "Units <units>"
        page.locator("button[aria-label='Units " + units + "']").click();
    }

    public void pressApply() {
        page.locator("button[aria-label='Apply']").click();
    }

    public void pressSend() {
        page.locator("button[aria-label='Send']").click();
    }

    public void pressMarkupPlus() {
        page.locator("button[aria-label='Increase Markup']").click();
    }

    public void pressMarkupMinus() {
        page.locator("button[aria-label='Decrease Markup']").click();
    }

    // ── M4 — Markup input assertion ────────────────────────────────────────────

    public double readMarkupValue() {
        String raw = page.locator("input[aria-label='Markup Value']").inputValue();
        return Double.parseDouble(raw);
    }

    // ── M4/M5 — Cell value assertions (auto-retrying where needed) ─────────────

    /**
     * Asserts that the named column cell for the given ISIN contains a numeric value.
     * Uses auto-retrying Playwright assertion to handle APPLY/SEND async rendering.
     */
    public void assertCellNumeric(String colId, String isin) {
        int rowIndex = findRowIndex(isin);
        // containsText with a digit pattern auto-retries until a number appears.
        // Playwright LocatorAssertions has no .as() — description goes in the failure
        // message via a wrapping AssertJ check if needed; here the locator is self-describing.
        assertThat(dataCell(colId, rowIndex)).containsText(Pattern.compile("\\d"));
    }

    /**
     * Asserts that the named column cell for the given ISIN is blank (null/empty formatter output).
     * No retry needed — this is a "nothing happened" assertion.
     */
    public void assertCellBlank(String colId, String isin) {
        int rowIndex = findRowIndex(isin);
        String text = dataCell(colId, rowIndex).textContent().trim();
        assertThat(text)
                .as("Cell [col-id='%s'] for ISIN '%s' should be blank", colId, isin)
                .isBlank();
    }
}
