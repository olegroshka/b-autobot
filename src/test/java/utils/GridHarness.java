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
 *   <li><b>Grid API path</b> — evaluate JavaScript to call {@code gridApi.forEachNode}
 *       (searches the <em>data model</em>, not the DOM), get the row index, then call
 *       {@code gridApi.ensureIndexVisible(rowIndex, 'middle')} to force AG Grid to
 *       render the row and scroll to it.</li>
 *   <li><b>Scroll-probe fallback</b> — if the grid API is not accessible (e.g. it is
 *       not exposed on {@code window}), reset the viewport to the top and page down
 *       incrementally, checking the DOM at each step.</li>
 * </ol>
 *
 * <p>No {@code Thread.sleep()} is used anywhere.  Post-scroll waits use
 * {@link Page#waitForFunction} to yield only when new rows are actually rendered.
 */
public class GridHarness {

    // ── Selectors ─────────────────────────────────────────────────────────────

    /** Scrollable viewport that AG Grid virtualises rows within. */
    private static final String VIEWPORT_SEL   = ".ag-body-viewport";

    /** Container that holds the visible row DOM nodes. */
    private static final String ROWS_CONTAINER = ".ag-center-cols-container";

    /** How long to wait after a scroll for AG Grid's row recycler to settle. */
    private static final int    RENDER_POLL_MS  = 100;

    /**
     * Maximum scroll iterations in Phase 3 before giving up (safety cap).
     * Each iteration covers roughly one viewport height.
     */
    private static final int    MAX_SCROLL_STEPS = 200;

    // ── JavaScript helpers (static strings — never interpolate user data) ─────

    /**
     * Inline JS helper that finds the AG Grid API through multiple paths:
     * <ol>
     *   <li>{@code window.gridApi} / {@code window.agGridApi} — set by some AG Grid demos.</li>
     *   <li>React fibre traversal — AG Grid React does not expose the API on {@code window},
     *       but it is accessible via the React internals property on the grid root element.</li>
     *   <li>{@code __agComponent.gridOptions.api} — older AG Grid versions.</li>
     * </ol>
     *
     * <p>This snippet is embedded verbatim inside the larger JS strings below.
     * Variable name: {@code _api}.
     */
    private static final String JS_FIND_API_SNIPPET =
            "  let _api = window.gridApi || window.agGridApi;" +
            "  if (!_api?.forEachNode) {" +
            "    const _el = document.querySelector('.ag-root-wrapper');" +
            "    const _fk = _el && Object.keys(_el).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactInternals'));" +
            "    if (_fk) {" +
            "      let _f = _el[_fk], _n = 0;" +
            "      while (_f && _n++ < 2000) {" +
            "        if (_f.memoizedProps?.api?.forEachNode) { _api = _f.memoizedProps.api; break; }" +
            "        const _s = _f.stateNode;" +
            "        if (_s && typeof _s === 'object' && !_s.nodeType && _s.api?.forEachNode) { _api = _s.api; break; }" +
            "        let _st = _f.memoizedState;" +
            "        while (_st) { if (_st.memoizedState?.current?.forEachNode) { _api = _st.memoizedState.current; break; } _st = _st.next; }" +
            "        if (_api) break;" +
            "        _f = _f.return;" +
            "      }" +
            "    }" +
            "    if (!_api?.forEachNode) _api = _el?.__agComponent?.gridOptions?.api || null;" +
            "  }";

    /** JS: returns the AG Grid API (null if not found). */
    private static final String JS_GET_API =
            "() => {" + JS_FIND_API_SNIPPET + " return _api; }";

    /**
     * JS (args = [colId, cellText]): uses the grid API to find the first row
     * whose {@code data[colId]} matches {@code cellText} exactly.
     * Returns the integer {@code rowIndex} or {@code null} if not found / no API.
     */
    private static final String JS_FIND_ROW_INDEX =
            "([col, val]) => {" +
            JS_FIND_API_SNIPPET +
            "  if (!_api?.forEachNode) return null;" +
            "  let found = null;" +
            "  _api.forEachNode(node => {" +
            "    if (found !== null) return;" +
            "    const raw = node.data?.[col];" +
            "    if (raw !== undefined && String(raw).trim() === String(val).trim()) {" +
            "      found = node.rowIndex;" +
            "    }" +
            "  });" +
            "  return found;" +
            "}";

    /**
     * JS (arg = rowIndex): calls {@code gridApi.ensureIndexVisible} to scroll the
     * grid to the given row and trigger DOM rendering.
     */
    private static final String JS_ENSURE_VISIBLE =
            "rowIdx => {" +
            JS_FIND_API_SNIPPET +
            "  if (_api?.ensureIndexVisible) {" +
            "    _api.ensureIndexVisible(rowIdx, 'middle');" +
            "    return true;" +
            "  }" +
            "  return false;" +
            "}";

    /** JS: returns the highest row-index currently present in the DOM. */
    private static final String JS_LAST_DOM_ROW_INDEX =
            "() => {" +
            "  const rows = document.querySelectorAll('.ag-center-cols-container [row-index]');" +
            "  return Math.max(-1, ...Array.from(rows)" +
            "    .map(el => parseInt(el.getAttribute('row-index'), 10)));" +
            "}";

    /** JS: returns true when the viewport cannot scroll further down. */
    private static final String JS_AT_BOTTOM =
            "() => { const vp = document.querySelector('.ag-body-viewport');" +
            "  return !vp || vp.scrollTop + vp.clientHeight >= vp.scrollHeight - 10; }";

    /** JS: scrolls the viewport down by 90 % of its client height. */
    private static final String JS_SCROLL_DOWN =
            "() => { const vp = document.querySelector('.ag-body-viewport');" +
            "  if (vp) vp.scrollTop += vp.clientHeight * 0.9; }";

    /** JS: resets the viewport to the very top. */
    private static final String JS_SCROLL_TOP =
            "() => { const vp = document.querySelector('.ag-body-viewport');" +
            "  if (vp) vp.scrollTop = 0; }";

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

        // ── Phase 2: Grid API (ensureIndexVisible) ────────────────────────────
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
        // Walk up to the [row-index='N'] ancestor, then down to the sibling col-id.
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
     * After {@code ensureIndexVisible} returns, wait for the row's DOM node to
     * actually appear (AG Grid rendering is async after the scroll).
     */
    private void waitForRowInDom(int rowIdx, Duration timeout) {
        // waitForFunction re-evaluates the predicate until it returns truthy.
        page.waitForFunction(
                "ri => !!document.querySelector(" +
                "  '.ag-center-cols-container [row-index=\"' + ri + '\"]')",
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
     * <p>After each scroll, the method waits for the {@code row-index} of the
     * last visible row to increase (proving AG Grid recycled the DOM nodes) before
     * re-checking, avoiding spin-polling against a stale DOM.
     */
    private Locator scrollProbe(String colId, String cellText, Instant deadline) {
        // Reset to top so we don't miss rows that precede the current scroll position.
        page.evaluate(JS_SCROLL_TOP);

        // Give AG Grid a moment to render the first set of rows after reset.
        // Using waitForFunction instead of sleep: wait until at least one row appears.
        page.waitForFunction(
                "() => document.querySelectorAll('.ag-center-cols-container [row-index]').length > 0",
                null,
                new Page.WaitForFunctionOptions().setTimeout(5000).setPollingInterval(RENDER_POLL_MS)
        );

        for (int step = 0; step < MAX_SCROLL_STEPS; step++) {
            // Check current viewport
            Locator found = fastFind(colId, cellText);
            if (found != null) {
                found.scrollIntoViewIfNeeded();
                return found;
            }

            // Have we run out of time?
            if (Instant.now().isAfter(deadline)) break;

            // Are we already at the bottom?
            boolean atBottom = Boolean.TRUE.equals(page.evaluate(JS_AT_BOTTOM));
            if (atBottom) break;

            // Capture the last visible row-index BEFORE scrolling.
            Object lastBefore = page.evaluate(JS_LAST_DOM_ROW_INDEX);
            int lastBeforeIdx = lastBefore instanceof Number n ? n.intValue() : -1;

            // Scroll down one viewport height.
            page.evaluate(JS_SCROLL_DOWN);

            // Wait until AG Grid has recycled/rendered new rows (last row-index increases)
            // OR until the bottom is reached (last-index won't grow at the end of the data).
            // We give up waiting for new rows after 2 s; the bottom-check on the next
            // iteration will then break the loop.
            try {
                final int capturedLast = lastBeforeIdx;
                page.waitForFunction(
                        "([before, atBottomSel]) => {" +
                        "  const vp = document.querySelector(atBottomSel);" +
                        "  if (vp && vp.scrollTop + vp.clientHeight >= vp.scrollHeight - 10)" +
                        "    return true;" +
                        "  const rows = document.querySelectorAll(" +
                        "    '.ag-center-cols-container [row-index]');" +
                        "  const last = Math.max(-1, ...Array.from(rows)" +
                        "    .map(el => parseInt(el.getAttribute('row-index'), 10)));" +
                        "  return last > before;" +
                        "}",
                        List.of(capturedLast, VIEWPORT_SEL),
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
