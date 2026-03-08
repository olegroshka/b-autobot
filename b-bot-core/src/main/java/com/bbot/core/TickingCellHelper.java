package com.bbot.core;

import com.bbot.core.config.BBotConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Instance-based helper for asserting on AG Grid "ticking" cells whose values
 * update at ~200–500 ms intervals.
 *
 * <p>Create via {@code new TickingCellHelper(page, config)} and call instance
 * methods that implement {@link CellAssertions}.
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
public class TickingCellHelper implements CellAssertions {

    private static final Logger LOG = LoggerFactory.getLogger(TickingCellHelper.class);

    private final Page page;
    private final BBotConfig config;

    /**
     * Creates a TickingCellHelper bound to the given page and config.
     *
     * @param page   the Playwright page to operate on
     * @param config the active configuration; may be {@code null} for defaults
     */
    public TickingCellHelper(Page page, BBotConfig config) {
        this.page = page;
        this.config = config;
    }

    // ── Polling config ────────────────────────────────────────────────────────

    private int instancePollMs() {
        return pollMs(config);
    }

    /**
     * Returns the polling interval (ms) for {@code waitForFunction} calls,
     * reading from the supplied config. Falls back to 150 ms.
     */
    static int pollMs(BBotConfig cfg) {
        if (cfg != null && cfg.hasPath("b-bot.ticking.pollMs"))
            return cfg.raw().getInt("b-bot.ticking.pollMs");
        return 150;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CellAssertions interface — instance methods
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public String waitForCellUpdate(String colId, int rowIndex, Duration timeout) {
        return doWaitForCellUpdate(page, colId, rowIndex, timeout, instancePollMs());
    }

    @Override
    public void assertCellValueInRange(String colId, int rowIndex,
                                        double min, double max, Duration timeout) {
        doAssertCellValueInRange(page, colId, rowIndex, min, max, timeout, instancePollMs());
    }

    @Override
    public void waitForCellFlash(String colId, int rowIndex, Duration timeout) {
        doWaitForCellFlash(page, colId, rowIndex, timeout, instancePollMs());
    }

    @Override
    public void waitForCellStable(String colId, int rowIndex, Duration timeout) {
        doWaitForCellStable(page, colId, rowIndex, timeout, instancePollMs());
    }

    @Override
    public String readStableValueAfterTick(String colId, int rowIndex,
                                            Duration flashTimeout, Duration stableTimeout) {
        doWaitForCellFlash(page, colId, rowIndex, flashTimeout, instancePollMs());
        doWaitForCellStable(page, colId, rowIndex, stableTimeout, instancePollMs());
        return page.locator(buildCellSelector(colId, rowIndex)).textContent().trim();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Implementation
    // ══════════════════════════════════════════════════════════════════════════

    private static String doWaitForCellUpdate(Page page, String colId, int rowIndex,
                                               Duration timeout, int pollMs) {
        String selector = buildCellSelector(colId, rowIndex);
        LOG.debug("Waiting for cell update: selector='{}', timeout={}ms", selector, timeout.toMillis());
        Locator cell = page.locator(selector);
        cell.scrollIntoViewIfNeeded();
        String initialValue = cell.textContent().trim();

        page.waitForFunction(
                "args => window.agGridProbes.ticking.hasCellValueChanged(args[0], args[1])",
                List.of(selector, initialValue),
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(pollMs)
        );
        return cell.textContent().trim();
    }

    private static void doAssertCellValueInRange(Page page, String colId, int rowIndex,
                                                  double min, double max, Duration timeout,
                                                  @SuppressWarnings("unused") int pollMs) {
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

    private static void doWaitForCellFlash(Page page, String colId, int rowIndex,
                                            Duration timeout, int pollMs) {
        String selector = buildCellSelector(colId, rowIndex);
        LOG.debug("Waiting for cell flash: selector='{}', timeout={}ms", selector, timeout.toMillis());
        page.locator(selector).scrollIntoViewIfNeeded();

        page.waitForFunction(
                "args => window.agGridProbes.ticking.isCellFlashing(args[0])",
                List.of(selector),
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(pollMs)
        );
    }

    private static void doWaitForCellStable(Page page, String colId, int rowIndex,
                                             Duration timeout, int pollMs) {
        String selector = buildCellSelector(colId, rowIndex);
        page.waitForFunction(
                "args => window.agGridProbes.ticking.isCellStable(args[0])",
                List.of(selector),
                new Page.WaitForFunctionOptions()
                        .setTimeout(timeout.toMillis())
                        .setPollingInterval(pollMs)
        );
    }

    // ── Pure utility methods (stateless helpers) ─────────────────────────────

    /**
     * Builds the canonical AG Grid cell CSS selector.
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
