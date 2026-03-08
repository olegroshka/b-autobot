package com.bbot.core;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.exception.BBotGridRowNotFoundException;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Virtualisation-aware utility for querying AG Grid rows.
 *
 * <h2>The Virtualisation Problem</h2>
 * AG Grid renders only the rows currently visible in the scroll viewport.
 * A row whose data exists in the model may have <em>no DOM node</em> if it has
 * scrolled out of view — meaning a plain {@code page.locator("text=XYZ")} will
 * simply not find it.
 *
 * <h2>Three-Phase Strategy</h2>
 * <ol>
 *   <li><b>Fast-path</b> — check the current DOM; free if the row is already visible.</li>
 *   <li><b>Grid API path</b> — call {@code window.agGridProbes.gridApi.findRowIndexByDataValue}
 *       (searches the <em>data model</em>, not the DOM), then call
 *       {@code window.agGridProbes.gridApi.ensureRowVisible(rowIndex)} to force AG Grid to
 *       render and scroll to the row.</li>
 *   <li><b>Scroll-probe fallback</b> — if the grid API is not accessible, reset the
 *       viewport to the top and page down incrementally, checking the DOM at each step.</li>
 * </ol>
 *
 * <p>No {@code Thread.sleep()} is used anywhere. Post-scroll waits use
 * {@link Page#waitForFunction} to yield only when new rows are actually rendered.
 * All JavaScript is delegated to {@code window.agGridProbes} which is injected
 * into every page via {@link PlaywrightManager#initContext()}.
 *
 * <h2>Configuration</h2>
 * All timeouts and poll intervals are read from the injected {@link BBotConfig}
 * so they can be tuned per environment without code changes:
 * <pre>
 *   b-bot.timeouts.gridFastPath   = 500ms   # phase-1 fast DOM scan
 *   b-bot.timeouts.gridRowInDom   = 5s      # phase-2 post-scroll row confirmation
 *   b-bot.timeouts.gridHasRows    = 5s      # phase-3 wait for any row before scrolling
 *   b-bot.timeouts.gridScrollStep = 2s      # phase-3 per-step scroll wait
 *   b-bot.grid.renderPollMs       = 100     # waitForFunction polling interval
 *   b-bot.grid.maxScrollSteps     = 200     # maximum scroll iterations
 * </pre>
 */
public class GridHarness implements GridQuery {

    private static final Logger LOG = LoggerFactory.getLogger(GridHarness.class);

    private static final String ROWS_CONTAINER = ".ag-center-cols-container";

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
    private static final String JS_NEW_ROWS_OR_BOTTOM =
            "([before]) => window.agGridProbes.scroll.isAtBottom() || " +
            "window.agGridProbes.dom.getLastDomRowIndex() > before";

    private final Page page;
    private final BBotConfig config;

    /**
     * Creates a GridHarness bound to the given page, using the given config for timeouts.
     */
    public GridHarness(Page page, BBotConfig config) {
        this.page = page;
        this.config = config;
    }


    /**
     * Finds the first AG Grid cell where {@code col-id} equals {@code colId}
     * and whose trimmed text equals {@code cellText} exactly.
     *
     * <p>Handles virtualisation transparently: if the row is not in the current
     * viewport it will scroll to it before returning.
     *
     * @return A {@link Locator} pointing to the matched cell (always visible in DOM)
     * @throws RuntimeException if the row is not found within the timeout
     */
    public Locator findRowByCellValue(String colId, String cellText, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);

        // Phase 1: Fast-path
        Locator fastResult = fastFind(colId, cellText);
        if (fastResult != null) {
            fastResult.scrollIntoViewIfNeeded();
            LOG.debug("GridHarness: fast-path found [col-id='{}'] text='{}'", colId, cellText);
            return fastResult;
        }

        // Phase 2: Grid API
        Object rowIdxObj = page.evaluate(JS_FIND_ROW_INDEX, List.of(colId, cellText));
        if (rowIdxObj instanceof Number num) {
            int rowIdx = num.intValue();
            if (rowIdx >= 0) {
                LOG.debug("GridHarness: grid-API path — row index={} for [col-id='{}'] text='{}'",
                          rowIdx, colId, cellText);
                page.evaluate(JS_ENSURE_VISIBLE, rowIdx);
                waitForRowInDom(rowIdx, Duration.ofMillis(cfgMs("b-bot.timeouts.gridRowInDom", 5_000)));
                Locator cell = cellLocator(colId, rowIdx);
                cell.scrollIntoViewIfNeeded();
                return cell;
            }
        }

        // Phase 3: Scroll-probe fallback
        return scrollProbe(colId, cellText, deadline);
    }

    /**
     * Returns the text content of the cell at {@code colId} in the same row as
     * {@code anchorCell}. Use after {@link #findRowByCellValue} to read sibling
     * columns without a second search.
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

    /**
     * Returns the trimmed text content of the cell at the given row-index and col-id.
     * Scrolls the cell into view first so it is rendered even if the row has been virtualised.
     */
    public String getCellText(String colId, int rowIndex) {
        Locator cell = cellLocator(colId, rowIndex);
        cell.scrollIntoViewIfNeeded();
        return cell.textContent().trim();
    }

    /** Returns a {@link Locator} for a specific cell by col-id and row-index. */
    public Locator cellLocator(String colId, int rowIndex) {
        return page.locator(String.format(
                "%s [row-index='%d'] [col-id='%s']",
                ROWS_CONTAINER, rowIndex, colId));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Locator fastFind(String colId, String cellText) {
        try {
            Locator locator = page
                    .locator(String.format("%s [col-id='%s']", ROWS_CONTAINER, colId))
                    .filter(new Locator.FilterOptions()
                            .setHasText(Pattern.compile("^" + Pattern.quote(cellText) + "$")));
            locator.first().waitFor(new Locator.WaitForOptions()
                    .setTimeout(cfgMs("b-bot.timeouts.gridFastPath", 500)));
            return locator.first();
        } catch (Exception e) {
            return null;
        }
    }

    private void waitForRowInDom(int rowIdx, Duration timeout) {
        page.waitForFunction(
                JS_ROW_IN_DOM, rowIdx,
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(cfgInt("b-bot.grid.renderPollMs", 100)));
    }

    private Locator scrollProbe(String colId, String cellText, Instant deadline) {
        LOG.debug("GridHarness: scroll-probe fallback for [col-id='{}'] text='{}'", colId, cellText);
        page.evaluate(JS_SCROLL_TOP);
        page.waitForFunction(JS_HAS_ROWS, null,
                new Page.WaitForFunctionOptions()
                        .setTimeout(cfgMs("b-bot.timeouts.gridHasRows", 5_000))
                        .setPollingInterval(cfgInt("b-bot.grid.renderPollMs", 100)));

        int maxSteps = cfgInt("b-bot.grid.maxScrollSteps", 200);
        long stepTimeoutMs = cfgMs("b-bot.timeouts.gridScrollStep", 2_000);
        int pollMs = cfgInt("b-bot.grid.renderPollMs", 100);

        for (int step = 0; step < maxSteps; step++) {
            Locator found = fastFind(colId, cellText);
            if (found != null) { found.scrollIntoViewIfNeeded(); return found; }
            if (Instant.now().isAfter(deadline)) break;
            if (Boolean.TRUE.equals(page.evaluate(JS_AT_BOTTOM))) break;

            Object lastBefore = page.evaluate(JS_LAST_DOM_ROW_INDEX);
            int lastBeforeIdx = lastBefore instanceof Number n ? n.intValue() : -1;
            page.evaluate(JS_SCROLL_DOWN);

            try {
                page.waitForFunction(JS_NEW_ROWS_OR_BOTTOM, List.of(lastBeforeIdx),
                        new Page.WaitForFunctionOptions()
                                .setTimeout(stepTimeoutMs)
                                .setPollingInterval(pollMs));
            } catch (Exception ignored) { /* at bottom or grid stalled — check next iter */ }
        }

        LOG.warn("GridHarness: exhaustive scroll search failed — col='{}', text='{}'", colId, cellText);
        throw new BBotGridRowNotFoundException(String.format(
                "GridHarness: no row found where [col-id='%s'] has text '%s' " +
                "after exhaustive scroll search.", colId, cellText),
                colId, cellText, Duration.between(Instant.now(), deadline).abs());
    }

    // ── Config helpers ────────────────────────────────────────────────────────

    /**
     * Reads a duration (in ms) from the active config.
     * Uses the instance config if provided, otherwise falls back to the registry.
     */
    private long cfgMs(String key, long defaultMs) {
        if (config != null && config.hasPath(key)) return config.getTimeout(key).toMillis();
        return defaultMs;
    }

    /**
     * Reads an integer from the active config.
     */
    private int cfgInt(String key, int defaultVal) {
        if (config != null && config.hasPath(key)) return config.raw().getInt(key);
        return defaultVal;
    }
}
