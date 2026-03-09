package com.bbot.sandbox.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Page Object Model for the AG Grid React Finance Demo.
 *
 * <p>Locator strategy (from CLAUDE.md rule #1):
 * Prioritise Playwright role/attribute locators over CSS. For AG Grid cells use
 * {@code [row-index='N'] [col-id='col']} selectors, never nth-child.
 *
 * <p>All JavaScript is delegated to {@code window.agGridProbes} which is injected
 * into every page via {@link com.bbot.core.PlaywrightManager#initContext()}.
 */
public class FinanceDemoPage {

    private final Page page;

    // ── Grid container ───────────────────────────────────────────────────────
    private final Locator gridRoot;
    private final Locator colsContainer;

    // ── Toolbar / controls ───────────────────────────────────────────────────
    private final Locator pauseButton;
    private final Locator resumeButton;

    public FinanceDemoPage(Page page) {
        this.page = page;

        // AG Grid root — all grids render with role="grid"
        this.gridRoot = page.locator("[role='grid']");
        // Virtualised row container
        this.colsContainer = page.locator(".ag-center-cols-container");

        // Finance demo toolbar buttons (role-based for stability)
        this.pauseButton  = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                                           new Page.GetByRoleOptions().setName("Pause"));
        this.resumeButton = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                                           new Page.GetByRoleOptions().setName("Resume"));
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    public void navigate(String url) {
        page.navigate(url);
        // assertThat().isVisible() has built-in retry; navigate waits for LOAD
        // so 15 s of built-in Playwright retry is sufficient.
        assertThat(gridRoot).isVisible();
    }

    // ── Grid state ───────────────────────────────────────────────────────────

    /** Returns true if the ticking data feed is active (not paused). */
    public boolean isLive() {
        return pauseButton.isVisible();
    }

    public void pauseFeed() {
        pauseButton.click();
    }

    public void resumeFeed() {
        resumeButton.click();
    }

    // ── Cell accessors ───────────────────────────────────────────────────────

    /**
     * Returns a Playwright {@link Locator} for an AG Grid data cell.
     * Scroll-into-view is NOT called here — caller decides when to scroll.
     *
     * @param colId    Value of the {@code col-id} attribute
     * @param rowIndex Zero-based visible row index
     */
    public Locator getCell(String colId, int rowIndex) {
        return colsContainer.locator(
                String.format("[row-index='%d'] [col-id='%s']", rowIndex, colId));
    }

    /**
     * Returns a Playwright {@link Locator} for an AG Grid header cell.
     *
     * @param colId Value of the {@code col-id} attribute on the header cell
     */
    public Locator getHeaderCell(String colId) {
        return page.locator(String.format(".ag-header-cell[col-id='%s']", colId));
    }

    /** Clicks a column header to trigger sort. */
    public void clickColumnHeader(String colId) {
        getHeaderCell(colId).click();
    }

    // ── Convenience read ─────────────────────────────────────────────────────

    /**
     * Reads a cell's text content after scrolling it into view.
     * Use {@link com.bbot.core.TickingCellHelper} when you need to wait for a value change.
     */
    public String getCellText(String colId, int rowIndex) {
        Locator cell = getCell(colId, rowIndex);
        cell.scrollIntoViewIfNeeded();
        return cell.textContent().trim();
    }

    // ── Grid-level assertions ─────────────────────────────────────────────────

    /** Asserts the grid header for {@code colId} is visible. */
    public void assertColumnVisible(String colId) {
        assertThat(getHeaderCell(colId)).isVisible();
    }

    /** Asserts the grid is rendered and contains at least one data row. */
    public void assertGridLoaded() {
        assertThat(gridRoot).isVisible();
        assertThat(colsContainer.locator("[row-index='0']")).isVisible();
    }

    // ── Row search (avoids relying on volatile row-index) ────────────────────

    /**
     * Finds the visible row index (0-based) of a ticker symbol.
     * Returns -1 if not found in the visible viewport.
     */
    public int findRowIndexForSymbol(String symbol) {
        Object result = page.evaluate(
                "sym => window.agGridProbes.dom.findRowIndexByText('ticker', sym)",
                symbol);
        return result instanceof Number ? ((Number) result).intValue() : -1;
    }

    // ── Column filter ─────────────────────────────────────────────────────────

    /**
     * Applies a column filter via the AG Grid JavaScript API.
     *
     * <p>AG Grid v33 uses different filter model formats per column type:
     * <ul>
     *   <li>Text filter: {@code {filterType:'text', type:'contains', filter:value}}</li>
     *   <li>Set filter (discrete-value columns such as Instrument):
     *       {@code {filterType:'set', values:[value]}}</li>
     * </ul>
     * This method tries text-filter format first.  If AG Grid's validation rejects it
     * (the column uses a set filter and throws an unhandled Promise rejection that
     * Playwright re-surfaces as a {@link com.microsoft.playwright.PlaywrightException}),
     * it retries with set-filter format.
     */
    public void applyColumnTextFilter(String colId, String value) {
        var args = java.util.Map.of("colId", colId, "value", value);
        try {
            page.evaluate(
                    "args => window.agGridProbes.filter.applyTextFilter(args.colId, args.value)",
                    args);
        } catch (com.microsoft.playwright.PlaywrightException e) {
            // Text-filter model rejected (column likely uses Set Filter) — retry.
            page.evaluate(
                    "args => window.agGridProbes.filter.applySetFilter(args.colId, args.value)",
                    args);
        }

        // Poll until every visible cell in the filtered column reflects the applied filter.
        page.waitForFunction(
                "([col, val]) => window.agGridProbes.dom.areAllVisibleCellsContaining(col, val)",
                List.of(colId, value));
    }

    /**
     * Clears all filters so every row is visible again.
     */
    public void clearColumnFilter(String colId) {
        page.evaluate("() => window.agGridProbes.filter.clearAllFilters()");
        page.waitForFunction("() => window.agGridProbes.dom.hasRowsInViewport()");
    }

    /**
     * Returns the trimmed text of every currently-rendered cell in {@code colId}.
     * Due to AG Grid virtualisation this reflects only the rows in the visible viewport.
     */
    public List<String> getAllVisibleCellTexts(String colId) {
        Object result = page.evaluate(
                "colId => window.agGridProbes.dom.getVisibleCellTexts(colId)",
                colId);
        if (result instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    // ── Package-visible for tests that need raw page handle ──────────────────

    Page getPage() {
        return page;
    }
}
