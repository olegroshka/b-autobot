package com.bbot.core;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helpers for asserting on AG Grid "ticking" cells whose values
 * update at ~200–500 ms intervals.
 *
 * <p><strong>Design rules:</strong>
 * <ul>
 *   <li>No {@code Thread.sleep()} — use Playwright's {@code waitForFunction} or
 *       {@code assertThat} auto-retry instead.</li>
 *   <li>All timeouts are explicit parameters so callers control retry budgets.</li>
 *   <li>All JavaScript delegates to {@code window.agGridProbes.ticking.*} which
 *       is injected via {@link PlaywrightManager#initContext()}.</li>
 * </ul>
 *
 * <h2>AG Grid cell locator convention</h2>
 * <pre>
 *   .ag-center-cols-container [row-index='N'] [col-id='price']
 * </pre>
 */
public final class TickingCellHelper {

    /** Polling interval used inside waitForFunction — short enough to catch a tick. */
    private static final int POLL_MS = 150;

    private TickingCellHelper() {}

    // ── 1. Wait for a cell to update ─────────────────────────────────────────

    /**
     * Blocks until the AG Grid cell at {@code colId / rowIndex} shows a value
     * different from whatever it displays at the moment of calling.
     *
     * @return The new cell text after the update
     */
    public static String waitForCellUpdate(Page page, String colId, int rowIndex, Duration timeout) {
        String selector = buildCellSelector(colId, rowIndex);
        Locator cell = page.locator(selector);
        cell.scrollIntoViewIfNeeded();
        String initialValue = cell.textContent().trim();

        page.waitForFunction(
                "args => window.agGridProbes.ticking.hasCellValueChanged(args[0], args[1])",
                List.of(selector, initialValue),
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(POLL_MS)
        );
        return cell.textContent().trim();
    }

    // ── 2. Assert cell value is within a numeric range ────────────────────────

    /**
     * Asserts (with Playwright auto-retry) that the cell text matches a numeric
     * pattern, then parses the value and checks it is within [{@code min}, {@code max}].
     */
    public static void assertCellValueInRange(
            Page page, String colId, int rowIndex,
            double min, double max, Duration timeout) {

        Locator cell = page.locator(buildCellSelector(colId, rowIndex));
        cell.scrollIntoViewIfNeeded();

        assertThat(cell)
                .hasText(Pattern.compile("[\\d.,]+"),
                         new com.microsoft.playwright.assertions.LocatorAssertions
                                 .HasTextOptions().setTimeout(timeout.toMillis()));

        double value = parseNumeric(cell.textContent());
        assertThat(value)
                .as("Cell [col-id='%s'][row-index=%d] value %.4f should be in [%.4f, %.4f]",
                    colId, rowIndex, value, min, max)
                .isBetween(min, max);
    }

    // ── 3. Wait for a cell flash ──────────────────────────────────────────────

    /**
     * Waits until AG Grid's tick-flash class {@code ag-cell-data-changed} is
     * applied to the target cell.
     */
    public static void waitForCellFlash(Page page, String colId, int rowIndex, Duration timeout) {
        String selector = buildCellSelector(colId, rowIndex);
        page.locator(selector).scrollIntoViewIfNeeded();

        page.waitForFunction(
                "args => window.agGridProbes.ticking.isCellFlashing(args[0])",
                List.of(selector),
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(POLL_MS)
        );
    }

    // ── 4. Wait for flash animation to finish ────────────────────────────────

    /**
     * Waits until the {@code ag-cell-data-changed} flash class is gone, meaning
     * the cell's value is stable and the DOM animation has completed.
     */
    public static void waitForCellStable(Page page, String colId, int rowIndex, Duration timeout) {
        String selector = buildCellSelector(colId, rowIndex);
        page.waitForFunction(
                "args => window.agGridProbes.ticking.isCellStable(args[0])",
                List.of(selector),
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(POLL_MS)
        );
    }

    // ── 5. Read a stable cell value after one tick cycle ─────────────────────

    /**
     * Waits for a tick, waits for the animation to finish, then returns the
     * stable text content — all without {@code Thread.sleep()}.
     */
    public static String readStableValueAfterTick(
            Page page, String colId, int rowIndex,
            Duration flashTimeout, Duration stableTimeout) {

        waitForCellFlash(page, colId, rowIndex, flashTimeout);
        waitForCellStable(page, colId, rowIndex, stableTimeout);
        return page.locator(buildCellSelector(colId, rowIndex)).textContent().trim();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Builds the canonical AG Grid cell CSS selector.
     * Using attribute selectors instead of nth-child keeps tests stable when
     * rows are filtered, sorted, or virtualised.
     */
    public static String buildCellSelector(String colId, int rowIndex) {
        return String.format(
                ".ag-center-cols-container [row-index='%d'] [col-id='%s']",
                rowIndex, colId);
    }

    /** Strips currency symbols, commas, and whitespace then parses as double. */
    public static double parseNumeric(String cellText) {
        String cleaned = cellText.replaceAll("[^\\d.]", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cell text '" + cellText + "' contains no parseable numeric content.");
        }
        return Double.parseDouble(cleaned);
    }
}
