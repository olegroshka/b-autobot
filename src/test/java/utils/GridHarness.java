package utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Virtualization-aware utility for querying AG Grid rows.
 *
 * <h2>The Virtualization Problem</h2>
 * AG Grid renders only the rows currently visible in the scroll viewport.
 * A row whose data exists in the model may have <em>no DOM node</em> if it has
 * scrolled out of view — meaning a plain {@code page.locator("text=XYZ")} will
 * simply not find it.
 *
 * <h2>Three-Phase Strategy</h2>
 * <ol>
 *   <li><b>Fast-path</b> — check the current DOM; free if the row is already visible.</li>
 *   <li><b>Grid API path</b> — call {@code window.agGridProbes.gridApi.findRowIndexByDataValue}
 *       (searches the <em>data model</em>, not the DOM), get the row index, then call
 *       {@code window.agGridProbes.gridApi.ensureRowVisible(rowIndex)} to force AG Grid to
 *       render the row and scroll to it.</li>
 *   <li><b>Scroll-probe fallback</b> — if the grid API is not accessible, reset the
 *       viewport to the top and page down incrementally, checking the DOM at each step.</li>
 * </ol>
 *
 * <p>No {@code Thread.sleep()} is used anywhere.  Post-scroll waits use
 * {@link Page#waitForFunction} to yield only when new rows are actually rendered.
 * All JavaScript is delegated to {@code window.agGridProbes} which is injected
 * into every page via {@link PlaywrightManager#initContext()}.
 */
public class GridHarness {

    // ── Selectors ─────────────────────────────────────────────────────────────

    /** Container that holds the visible row DOM nodes. */
    private static final String ROWS_CONTAINER = ".ag-center-cols-container";

    /** How long to wait after a scroll for AG Grid's row recycler to settle. */
    private static final int RENDER_POLL_MS = 100;

    /**
     * Maximum scroll iterations in Phase 3 before giving up (safety cap).
     * Each iteration covers roughly one viewport height.
     */
    private static final int MAX_SCROLL_STEPS = 200;

    // ── Probe-based JavaScript predicates ─────────────────────────────────────
    // All of these delegate to window.agGridProbes (injected via addInitScript).
    // Short one-liners — no inline comments that could cause parse errors.

    private static final String JS_FIND_ROW_INDEX =
            "([col, val]) => window.agGridProbes.gridApi.findRowIndexByDataValue(col, val)";

    private static final String JS_ENSURE_VISIBLE =
            "rowIdx => window.agGridProbes.gridApi.ensureRowVisible(rowIdx)";

    private static final String JS_ROW_IN_DOM =
            "ri => window.agGridProbes.dom.isRowInDom(ri)";

    private static final String JS_HAS_ROWS =
            "() => window.agGridProbes.dom.hasRowsInViewport()";

    private static final String JS_LAST_DOM_ROW_INDEX =
            "() => window.agGridProbes.dom.getLastDomRowIndex()";

    private static final String JS_AT_BOTTOM =
            "() => window.agGridProbes.scroll.isAtBottom()";

    private static final String JS_SCROLL_DOWN =
            "() => window.agGridProbes.scroll.scrollDown()";

    private static final String JS_SCROLL_TOP =
            "() => window.agGridProbes.scroll.scrollToTop()";

    /** Wait predicate: new rows rendered OR viewport reached the bottom. */
    private static final String JS_NEW_ROWS_OR_BOTTOM =
            "([before]) => window.agGridProbes.scroll.isAtBottom() || " +
            "window.agGridProbes.dom.getLastDomRowIndex() > before";

    // ── Instance fields ───────────────────────────────────────────────────────

    private final Page page;

    public GridHarness(Page page) {
        this.page = page;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Public API
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Finds the first AG Grid cell where {@code col-id} equals {@code colId}
     * and whose trimmed text equals {@code cellText} <em>exactly</em>.
     *
     * <p>The method transparently handles virtualization: if the row is not in
     * the current viewport it will scroll to it before returning.
     *
     * @param colId    Value of the AG Grid {@code col-id} attribute
     * @param cellText Exact text the target cell must display
     * @param timeout  Total time budget across all three phases
     * @return A {@link Locator} pointing to the matched cell (always visible in DOM)
     * @throws RuntimeException if the row is not found within the timeout
     */
    public Locator findRowByCellValue(String colId, String cellText, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);

        // ── Phase 1: Fast-path ────────────────────────────────────────────────
        Locator fastResult = fastFind(colId, cellText);
        if (fastResult != null) {
            fastResult.scrollIntoViewIfNeeded();
            return fastResult;
        }

        // ── Phase 2: Grid API (ensureRowVisible) ──────────────────────────────
        Object rowIdxObj = page.evaluate(JS_FIND_ROW_INDEX, List.of(colId, cellText));
        if (rowIdxObj instanceof Number num) {
            int rowIdx = num.intValue();
            if (rowIdx >= 0) {
                page.evaluate(JS_ENSURE_VISIBLE, rowIdx);
                waitForRowInDom(rowIdx, Duration.ofSeconds(5));
                Locator cell = cellLocator(colId, rowIdx);
                cell.scrollIntoViewIfNeeded();
                return cell;
            }
        }

        // ── Phase 3: Scroll-probe fallback ────────────────────────────────────
        return scrollProbe(colId, cellText, deadline);
    }

    /**
     * Returns the text content of the cell at {@code colId} in the same row as
     * {@code anchorCell}.  Use after {@link #findRowByCellValue} to read sibling
     * columns without a second search.
     *
     * @param anchorCell A cell locator previously returned by {@link #findRowByCellValue}
     * @param colId      The column to read from the same row
     */
    public String getSiblingCellText(Locator anchorCell, String colId) {
        return anchorCell
                .locator("xpath=ancestor::*[@row-index][1]")
                .locator(String.format("[col-id='%s']", colId))
                .textContent()
                .trim();
    }

    /**
     * Returns the integer {@code row-index} attribute of the row containing
     * the given cell locator, or {@code -1} if the attribute cannot be found.
     */
    public int getRowIndex(Locator cellLocator) {
        String raw = cellLocator
                .locator("xpath=ancestor::*[@row-index][1]")
                .getAttribute("row-index");
        if (raw == null) return -1;
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Phase 1: try to find the cell in the current DOM without scrolling.
     * Waits at most 500 ms so this stays cheap.
     */
    private Locator fastFind(String colId, String cellText) {
        try {
            Locator locator = page
                    .locator(String.format("%s [col-id='%s']", ROWS_CONTAINER, colId))
                    .filter(new Locator.FilterOptions()
                            .setHasText(Pattern.compile("^" + Pattern.quote(cellText) + "$")));
            locator.first().waitFor(
                    new Locator.WaitForOptions().setTimeout(500));
            return locator.first();
        } catch (Exception e) {
            return null;  // not visible yet — continue to Phase 2
        }
    }

    /**
     * After {@code ensureRowVisible} returns, wait for the row's DOM node to
     * actually appear (AG Grid rendering is async after the scroll).
     */
    private void waitForRowInDom(int rowIdx, Duration timeout) {
        page.waitForFunction(
                JS_ROW_IN_DOM,
                rowIdx,
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(RENDER_POLL_MS)
        );
    }

    /**
     * Phase 3: scroll the AG Grid viewport from top to bottom, checking the
     * current batch of DOM rows at each step.
     *
     * <p>After each scroll, the method waits for the last visible row-index to
     * increase (proving AG Grid recycled the DOM nodes) before re-checking,
     * avoiding spin-polling against a stale DOM.
     */
    private Locator scrollProbe(String colId, String cellText, Instant deadline) {
        page.evaluate(JS_SCROLL_TOP);

        // Wait until at least one row is rendered after the scroll reset.
        page.waitForFunction(
                JS_HAS_ROWS,
                null,
                new Page.WaitForFunctionOptions().setTimeout(5000).setPollingInterval(RENDER_POLL_MS)
        );

        for (int step = 0; step < MAX_SCROLL_STEPS; step++) {
            Locator found = fastFind(colId, cellText);
            if (found != null) {
                found.scrollIntoViewIfNeeded();
                return found;
            }

            if (Instant.now().isAfter(deadline)) break;

            if (Boolean.TRUE.equals(page.evaluate(JS_AT_BOTTOM))) break;

            Object lastBefore = page.evaluate(JS_LAST_DOM_ROW_INDEX);
            int lastBeforeIdx = lastBefore instanceof Number n ? n.intValue() : -1;

            page.evaluate(JS_SCROLL_DOWN);

            // Wait until AG Grid renders new rows OR reaches the bottom of the data.
            // Give up after 2 s; the bottom-check on the next iteration will then
            // break the loop.
            try {
                page.waitForFunction(
                        JS_NEW_ROWS_OR_BOTTOM,
                        List.of(lastBeforeIdx),
                        new Page.WaitForFunctionOptions()
                                .setTimeout(2000)
                                .setPollingInterval(RENDER_POLL_MS)
                );
            } catch (Exception ignored) {
                // Timeout: either at bottom or grid is not updating — break next iteration
            }
        }

        throw new RuntimeException(String.format(
                "GridHarness: no row found where [col-id='%s'] has text '%s' " +
                "after exhaustive scroll search.", colId, cellText));
    }

    /**
     * Returns the trimmed text content of the cell at the given row-index and col-id.
     * Scrolls the cell into view first so it is rendered even if the row has been
     * virtualised out of the visible viewport.
     *
     * <p>For a virtualisation-safe search <em>by value</em>, use
     * {@link #findRowByCellValue} instead.
     *
     * @param colId    AG Grid {@code col-id} attribute value
     * @param rowIndex Zero-based {@code row-index} attribute value
     * @return Trimmed cell text
     */
    public String getCellText(String colId, int rowIndex) {
        Locator cell = cellLocator(colId, rowIndex);
        cell.scrollIntoViewIfNeeded();
        return cell.textContent().trim();
    }

    /**
     * Returns a {@link Locator} for a specific cell.
     * Made public so callers can chain Playwright assertions directly.
     */
    public Locator cellLocator(String colId, int rowIndex) {
        return page.locator(String.format(
                "%s [row-index='%d'] [col-id='%s']",
                ROWS_CONTAINER, rowIndex, colId));
    }
}
