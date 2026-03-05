package utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helpers for asserting on AG Grid Finance Demo "ticking" cells whose values
 * update at ~200–500 ms intervals.
 *
 * <p><strong>Design rules:</strong>
 * <ul>
 *   <li>No {@code Thread.sleep()} — use Playwright's {@code waitForFunction} or
 *       {@code assertThat} auto-retry instead.</li>
 *   <li>All timeouts are explicit parameters so callers control retry budgets.</li>
 *   <li>JavaScript expressions are self-contained strings; no dynamic code injection.</li>
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

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Wait for a cell to update (value changes from its current content)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Blocks until the AG Grid cell at {@code colId / rowIndex} shows a value
     * different from whatever it displays at the moment of calling.
     *
     * <p>Uses {@code page.waitForFunction()} so no thread is blocked with sleep.
     *
     * @param page     Playwright {@link Page}
     * @param colId    AG Grid column id attribute value (e.g. {@code "price"})
     * @param rowIndex AG Grid {@code row-index} attribute value (0-based)
     * @param timeout  Maximum time to wait for the cell to tick
     * @return The new cell text after the update
     */
    public static String waitForCellUpdate(Page page, String colId, int rowIndex, Duration timeout) {
        String selector = buildCellSelector(colId, rowIndex);
        Locator cell = page.locator(selector);

        // Ensure the cell is in view before reading — AG Grid virtualises rows.
        cell.scrollIntoViewIfNeeded();

        String initialValue = cell.textContent().trim();

        // waitForFunction polls the given JS predicate until it returns truthy.
        // Passing [selector, initialValue] as a JSON array avoids string interpolation
        // inside the expression (prevents accidental injection).
        page.waitForFunction(
                "args => { " +
                "  const el = document.querySelector(args[0]); " +
                "  return el && el.textContent.trim() !== '' && el.textContent.trim() !== args[1]; " +
                "}",
                List.of(selector, initialValue),
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(POLL_MS)
        );

        return cell.textContent().trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Assert that a cell's numeric value is within an expected range
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Asserts (with Playwright auto-retry) that the cell text matches a numeric
     * pattern, then parses the value and checks it is within [{@code min}, {@code max}].
     *
     * @param page     Playwright {@link Page}
     * @param colId    AG Grid column id
     * @param rowIndex Row index (0-based)
     * @param min      Inclusive lower bound
     * @param max      Inclusive upper bound
     * @param timeout  Retry budget for the text-match assertion
     */
    public static void assertCellValueInRange(
            Page page, String colId, int rowIndex,
            double min, double max, Duration timeout) {

        Locator cell = page.locator(buildCellSelector(colId, rowIndex));
        cell.scrollIntoViewIfNeeded();

        // Wait until cell shows any numeric content (handles empty/loading state).
        // hasText(Pattern, HasTextOptions) — use LocatorAssertions.HasTextOptions, not Locator.WaitForOptions.
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

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Wait for a cell flash (ag-cell-data-changed class appears)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Waits until AG Grid's tick-flash class {@code ag-cell-data-changed} is
     * applied to the target cell, confirming at least one live update occurred.
     *
     * <p>This is more reliable than watching for a specific value when you only
     * need to prove the cell is live, not what value it currently holds.
     *
     * @param page     Playwright {@link Page}
     * @param colId    AG Grid column id
     * @param rowIndex Row index (0-based)
     * @param timeout  Maximum wait for the flash class to appear
     */
    public static void waitForCellFlash(Page page, String colId, int rowIndex, Duration timeout) {
        String selector = buildCellSelector(colId, rowIndex);
        page.locator(selector).scrollIntoViewIfNeeded();

        // Support two AG Grid flash mechanisms:
        //  • ag-cell-data-changed  — standard enableCellChangeFlash=true
        //  • ag-value-change-value-highlight — agAnimateShowChangeCellRenderer (the finance demo)
        page.waitForFunction(
                "args => { " +
                "  const el = document.querySelector(args[0]); " +
                "  return el && (" +
                "    el.classList.contains('ag-cell-data-changed') || " +
                "    !!el.querySelector('.ag-value-change-value-highlight')" +
                "  );" +
                "}",
                List.of(selector),
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(POLL_MS)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Wait for flash animation to FINISH (cell stabilises after a tick)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Waits until the {@code ag-cell-data-changed} flash class is gone, meaning
     * the cell's value is stable and the DOM animation has completed.
     *
     * <p>Useful when you want to read a value without risk of catching a mid-tick
     * state. Call this <em>after</em> {@link #waitForCellFlash}.
     *
     * @param page     Playwright {@link Page}
     * @param colId    AG Grid column id
     * @param rowIndex Row index (0-based)
     * @param timeout  Maximum wait for the animation to complete
     */
    public static void waitForCellStable(Page page, String colId, int rowIndex, Duration timeout) {
        String selector = buildCellSelector(colId, rowIndex);

        page.waitForFunction(
                "args => { " +
                "  const el = document.querySelector(args[0]); " +
                "  return el && !el.classList.contains('ag-cell-data-changed'); " +
                "}",
                List.of(selector),
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(POLL_MS)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Convenience: read a cell value after it has stabilised post-flash
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Waits for a tick to occur then waits for the animation to finish, and
     * finally returns the stable text content — all without {@code Thread.sleep()}.
     *
     * @param page          Playwright {@link Page}
     * @param colId         AG Grid column id
     * @param rowIndex      Row index (0-based)
     * @param flashTimeout  Time budget for the tick to occur
     * @param stableTimeout Time budget for the animation to finish after the tick
     * @return The stable cell text after one full tick cycle
     */
    public static String readStableValueAfterTick(
            Page page, String colId, int rowIndex,
            Duration flashTimeout, Duration stableTimeout) {

        waitForCellFlash(page, colId, rowIndex, flashTimeout);
        waitForCellStable(page, colId, rowIndex, stableTimeout);
        return page.locator(buildCellSelector(colId, rowIndex)).textContent().trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the canonical AG Grid cell CSS selector used throughout this class.
     * Using attribute selectors instead of nth-child keeps tests stable when
     * rows are filtered, sorted, or virtualised.
     */
    static String buildCellSelector(String colId, int rowIndex) {
        return String.format(
                ".ag-center-cols-container [row-index='%d'] [col-id='%s']",
                rowIndex, colId);
    }

    /** Strips currency symbols, commas, and whitespace then parses as double. */
    static double parseNumeric(String cellText) {
        String cleaned = cellText.replaceAll("[^\\d.]", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cell text '" + cellText + "' contains no parseable numeric content.");
        }
        return Double.parseDouble(cleaned);
    }
}
