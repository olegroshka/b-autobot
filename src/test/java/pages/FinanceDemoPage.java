package pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.time.Duration;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Page Object Model for the AG Grid React Finance Demo.
 *
 * <p>Locator strategy (from CLAUDE.md rule #1):
 * Prioritise Playwright role/attribute locators over CSS. For AG Grid cells use
 * {@code [row-index='N'] [col-id='col']} selectors, never nth-child.
 */
public class FinanceDemoPage {

    private final Page page;

    // ── Grid container ───────────────────────────────────────────────────────
    private final Locator gridRoot;
    private final Locator colsContainer;

    // ── Toolbar / controls (adjust selectors to match the actual demo DOM) ───
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
        // Wait for the grid to be rendered before any interaction
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
     * Use {@link utils.TickingCellHelper} when you need to wait for a value change.
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
     *
     * <p>Uses a JavaScript search so it works without iterating over Locators.
     */
    public int findRowIndexForSymbol(String symbol) {
        Object result = page.evaluate(
                "sym => { " +
                "  const rows = [...document.querySelectorAll(" +
                "      '.ag-center-cols-container [col-id=\"symbol\"]')];" +
                "  const match = rows.find(el => el.textContent.trim() === sym);" +
                "  if (!match) return -1;" +
                "  const row = match.closest('[row-index]');" +
                "  return row ? parseInt(row.getAttribute('row-index'), 10) : -1;" +
                "}",
                symbol);
        return result instanceof Number ? ((Number) result).intValue() : -1;
    }

    // ── Column filter ─────────────────────────────────────────────────────────

    // ── Shared JS snippet: finds the AG Grid API via window globals or React fibre ─

    /** Embedded in every JS evaluate call that needs the grid API. */
    private static final String JS_FIND_API =
            "  let _api = window.gridApi || window.agGridApi;" +
            "  if (!_api?.setFilterModel) {" +
            "    const _el = document.querySelector('.ag-root-wrapper');" +
            "    const _fk = _el && Object.keys(_el).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactInternals'));" +
            "    if (_fk) {" +
            "      let _f = _el[_fk], _n = 0;" +
            "      while (_f && _n++ < 2000) {" +
            "        if (_f.memoizedProps?.api?.setFilterModel) { _api = _f.memoizedProps.api; break; }" +
            "        const _s = _f.stateNode;" +
            "        if (_s && typeof _s === 'object' && !_s.nodeType && _s.api?.setFilterModel) { _api = _s.api; break; }" +
            "        let _st = _f.memoizedState;" +
            "        while (_st) { if (_st.memoizedState?.current?.setFilterModel) { _api = _st.memoizedState.current; break; } _st = _st.next; }" +
            "        if (_api) break; _f = _f.return;" +
            "      }" +
            "    }" +
            "    if (!_api?.setFilterModel) _api = _el?.__agComponent?.gridOptions?.api || null;" +
            "  }";

    /**
     * Applies a column filter via the AG Grid JavaScript API.
     *
     * <p>The Finance Demo's React app does not expose {@code window.gridApi}.
     * The API is located via React fibre traversal.
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
        String textFilterJs =
                "(args) => {" + JS_FIND_API +
                "  if (!_api) return;" +
                "  const m = {};" +
                "  m[args.colId] = { filterType: 'text', type: 'contains', filter: args.value };" +
                "  _api.setFilterModel(m);" +
                "}";
        String setFilterJs =
                "(args) => {" + JS_FIND_API +
                "  if (!_api) return;" +
                "  const m = {};" +
                "  m[args.colId] = { filterType: 'set', values: [args.value] };" +
                "  _api.setFilterModel(m);" +
                "}";

        try {
            page.evaluate(textFilterJs, args);
        } catch (com.microsoft.playwright.PlaywrightException e) {
            // Text-filter model rejected (column likely uses Set Filter) — retry.
            page.evaluate(setFilterJs, args);
        }

        // Poll until every visible cell in the filtered column reflects the applied filter.
        page.waitForFunction(
                "([col, val]) => {" +
                "  const cells = document.querySelectorAll('.ag-center-cols-container [col-id=\"' + col + '\"]');" +
                "  return cells.length > 0 && Array.from(cells).every(" +
                "    c => c.textContent.toLowerCase().includes(val.toLowerCase()));" +
                "}",
                java.util.List.of(colId, value));
    }

    /**
     * Clears all filters so every row is visible again.
     */
    public void clearColumnFilter(String colId) {
        page.evaluate(
                "() => {" +
                JS_FIND_API +
                "  if (_api) _api.setFilterModel(null);" +
                "}");

        page.waitForFunction(
                "() => document.querySelector('.ag-center-cols-container [row-index]') !== null");
    }

    /**
     * Returns the trimmed text of every currently-rendered cell in {@code colId}.
     * Due to AG Grid virtualisation this reflects only the rows in the visible viewport.
     */
    public List<String> getAllVisibleCellTexts(String colId) {
        Object result = page.evaluate(
                "colId => [...document.querySelectorAll(" +
                "  '.ag-center-cols-container [col-id=\"' + colId + '\"]'" +
                ")].map(el => el.textContent.trim())",
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
