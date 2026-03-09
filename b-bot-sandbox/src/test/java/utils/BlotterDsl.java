package com.bbot.sandbox.utils;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.bbot.core.TickingCellHelper;
import com.bbot.core.registry.AppContext;
import com.microsoft.playwright.options.RequestOptions;

import java.time.Duration;
import java.util.List;
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

    private final Page       page;
    private final AppContext ctx;
    private final TickingCellHelper tickingCells;

    public BlotterDsl(Page page, AppContext ctx) {
        this.page = page;
        this.ctx  = ctx;
        this.tickingCells = new TickingCellHelper(page, ctx.config());
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    /** Opens the blotter as the default 'trader' user (non-admin). */
    public void openBlotter() {
        openBlotter("trader");
    }

    /**
     * Opens the blotter as the given user, passing {@code configUrl} so the app
     * can fetch {@code isPTAdmin} from {@link MockConfigServer}.
     *
     * @param user  e.g. {@code "trader"} or {@code "algo_trader"}
     */
    public void openBlotter(String user) {
        String url = ctx.getWebUrl()
                + "?user=" + user
                + "&configUrl=" + ctx.getOtherAppApiBase("config-service");
        page.navigate(url);
        // Wait for AG Grid to render the first data row.
        // type="module" scripts are deferred, so DOMCONTENTLOADED fires before React runs;
        // waiting for a rendered row guarantees the grid is fully initialised.
        page.waitForSelector(".ag-center-cols-container [row-index='0']");
    }

    // ── M0 ─────────────────────────────────────────────────────────────────────

    public void assertTitle(String expected) {
        // Pattern.quote() produces \Q...\E which is Java-only; JavaScript (used by
        // Playwright's hasTitle poller) treats \Q as literal 'Q'.  Use a plain
        // contains-style waitForFunction instead, which runs entirely in Java/JS.
        page.waitForFunction("exp => document.title.includes(exp)", expected);
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
        // Poll until the probe returns a valid row index (handles async grid updates).
        page.waitForFunction(
                "isin => { var idx = window.agGridProbes.dom.findRowIndexByText('isin', isin);" +
                " return typeof idx === 'number' && idx >= 0; }",
                isin);
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
        return tickingCells.waitForCellUpdate(
                colId, rowIndex, Duration.ofSeconds(seconds));
    }

    public void assertCellMatchesPattern(String colId, int rowIndex, String pattern) {
        /* Use Playwright's auto-retrying assertion so ticking cells are stable.
         * containsText(Pattern) polls until the cell text matches — no timing race. */
        assertThat(dataCell(colId, rowIndex)).containsText(Pattern.compile(pattern));
    }

    public void assertCellNotBlankByIndex(String colId, int rowIndex) {
        String text = dataCell(colId, rowIndex).textContent().trim();
        assertThat(text).as("Cell [col-id='%s'][row-index=%d]", colId, rowIndex).isNotBlank();
    }

    public void waitForCellValueChange(String colId, int rowIndex, int seconds) {
        tickingCells.waitForCellUpdate(colId, rowIndex, Duration.ofSeconds(seconds));
    }

    // ── M3 — REST API ──────────────────────────────────────────────────────────

    public APIResponse submitInquiry(String isin, String notional, String side, String client) {
        APIRequestContext req = page.context().request();
        return req.post(
                ctx.getApiBaseUrl() + "/api/inquiry",
                RequestOptions.create().setData(Map.of(
                        "isin",     isin,
                        "notional", Long.parseLong(notional),
                        "side",     side,
                        "client",   client)));
    }

    public APIResponse submitInquiry(String isin) {
        APIRequestContext req = page.context().request();
        return req.post(
                ctx.getApiBaseUrl() + "/api/inquiry",
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
        // Skip if already selected — Ctrl+click on a selected row would DEselect it.
        // This makes selectRowByIsin idempotent (calling it twice for the same ISIN is safe).
        Boolean alreadySelected = (Boolean) page.evaluate(
                "idx => Array.from(document.querySelectorAll('.ag-row[row-index=\"' + idx + '\"]'))" +
                        ".some(function(r){return r.classList.contains('ag-row-selected');})",
                rowIndex);
        if (Boolean.TRUE.equals(alreadySelected)) return;
        // Ctrl+click in multiRow mode: adds the row to the current selection without replacing.
        // force:true bypasses Playwright's stability check — the live price simulator ticks every
        // 400ms causing cell flash-animations that can fail the stability gate.
        page.locator(".ag-pinned-left-cols-container [row-index='" + rowIndex + "']")
                .click(new Locator.ClickOptions()
                        .setModifiers(List.of(com.microsoft.playwright.options.KeyboardModifier.CONTROL))
                        .setForce(true));
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

    public void pressReleasePt() {
        page.locator("button[aria-label='Release PT']").click();
    }

    public void assertReleasePtEnabled() {
        assertThat(page.locator("button[aria-label='Release PT']")).isEnabled();
    }

    public void assertReleasePtDisabled() {
        assertThat(page.locator("button[aria-label='Release PT']")).isDisabled();
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
