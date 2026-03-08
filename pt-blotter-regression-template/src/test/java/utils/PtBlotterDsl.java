package utils;

import com.bbot.core.TickingCellHelper;
import com.bbot.core.registry.AppContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.time.Duration;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Domain-Specific Language (DSL) for the PT-Blotter UI and REST API.
 *
 * <p>This is the <em>only</em> layer allowed to call Playwright directly.
 * Step definitions delegate entirely to DSL methods and never hold a {@link Page}
 * reference themselves. This separation keeps scenarios readable and concentrates
 * all locator knowledge in one place.
 *
 * <h2>Template customisation points</h2>
 * <ul>
 *   <li>Rename this class and its package to match your application.</li>
 *   <li>Replace navigation methods with your own page URLs and wait conditions.</li>
 *   <li>Add one method per observable user action or assertion your scenarios need.</li>
 *   <li>Use {@link AppContext#getWebUrl()} / {@link AppContext#getApiBaseUrl()} for
 *       environment-portable URLs — never hardcode host names or ports.</li>
 *   <li>Use {@link AppContext#getUser(String)} to look up named users from config
 *       ({@code b-bot.apps.blotter.users.trader = doej}).</li>
 * </ul>
 *
 * <h2>Key patterns used here</h2>
 * <ul>
 *   <li>No {@code Thread.sleep()} — all waits use Playwright's built-in retry.</li>
 *   <li>All JavaScript goes through {@code window.agGridProbes.*} from {@code b-bot-core}.</li>
 *   <li>Row lookup is virtualisation-safe: uses the JS probe to find the row index
 *       then builds a stable CSS attribute selector.</li>
 *   <li>Cell assertions use {@code assertThat(locator)} for auto-retry on ticking values.</li>
 * </ul>
 */
public final class PtBlotterDsl {

    /** Timeout for finding a row by ISIN in the grid (handles slow CI environments). */
    private static final long ROW_LOOKUP_MS = 10_000;

    /** Toolbar aria-labels — must match the React component's aria-label attributes. */
    private static final String LABEL_SOURCE  = "Ref Source";
    private static final String LABEL_SIDE    = "Ref Side";
    private static final String LABEL_MARKUP  = "Markup Value";
    private static final String LABEL_UNITS_C = "Units c";
    private static final String LABEL_UNITS_BP = "Units bp";
    private static final String LABEL_APPLY   = "Apply";
    private static final String LABEL_SEND    = "Send";
    private static final String LABEL_RELEASE = "Release PT";

    private final Page       page;
    private final AppContext ctx;
    private final TickingCellHelper tickingCells;

    public PtBlotterDsl(Page page, AppContext ctx) {
        this.page = page;
        this.ctx  = ctx;
        this.tickingCells = new TickingCellHelper(page, ctx.config());
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Opens the blotter as the default trader declared in config:
     * {@code b-bot.apps.blotter.users.trader}.
     */
    public void openBlotter() {
        openBlotter(ctx.getUser("trader").orElse("doej"));
    }

    /**
     * Navigates to the blotter as the given user and waits for the grid to render.
     *
     * <p>The Config Service URL is appended as a query parameter so the React app
     * can fetch the user's {@code isPTAdmin} flag on startup.
     */
    public void openBlotter(String user) {
        String configUrl = ctx.getOtherAppApiBase("config-service");
        String url = ctx.getWebUrl() + "?user=" + user
                + (configUrl != null ? "&configUrl=" + configUrl : "");
        try {
            page.navigate(url);
            page.waitForSelector(".ag-center-cols-container [row-index='0']");
        } catch (com.microsoft.playwright.PlaywrightException e) {
            throw new AssertionError(
                    "Could not open PT-Blotter at " + ctx.getWebUrl() +
                    "\nIs the mock UAT environment running? " +
                    "Start it with: scripts/start-mock-uat.sh  (or .bat on Windows)\n" +
                    "Original error: " + e.getMessage(), e);
        }
    }

    // ── Page-level assertions ──────────────────────────────────────────────────

    /**
     * Asserts the browser page title contains the expected substring.
     * Uses {@code waitForFunction} for auto-retry (title may update after navigation).
     */
    public void assertTitleContains(String expected) {
        page.waitForFunction(
                "exp => document.title.includes(exp)",
                expected);
    }

    /**
     * Asserts the AG Grid centre container has at least one rendered row.
     */
    public void assertGridRendered() {
        page.waitForSelector(".ag-center-cols-container [row-index='0']");
    }

    // ── Grid schema assertions ────────────────────────────────────────────────

    /**
     * Asserts that a column header with the given {@code col-id} is present in the grid.
     */
    public void assertGridHasColumn(String colId) {
        page.waitForSelector(".ag-header-cell[col-id='" + colId + "']");
    }

    /**
     * Asserts that the grid has rendered at least {@code minRows} rows.
     */
    public void assertGridHasMinRows(int minRows) {
        page.waitForFunction(
                "n => document.querySelectorAll('.ag-center-cols-container [row-index]').length >= n",
                minRows,
                new Page.WaitForFunctionOptions().setTimeout(10_000));
    }

    // ── Row assertions ────────────────────────────────────────────────────────

    /**
     * Asserts that the row identified by {@code isin} shows the expected {@code status}.
     */
    public void assertRowStatus(String isin, String expectedStatus) {
        int rowIndex = findRowIndex(isin);
        Locator statusCell = page.locator(TickingCellHelper.buildCellSelector("status", rowIndex));
        assertThat(statusCell).hasText(
                expectedStatus,
                new com.microsoft.playwright.assertions.LocatorAssertions
                        .HasTextOptions().setTimeout(5_000));
    }

    /**
     * Asserts that the given column cell for the row identified by {@code isin}
     * contains a numeric value (matches {@code [0-9]+[.][0-9]+}).
     */
    public void assertCellNumeric(String colId, String isin) {
        int rowIndex = findRowIndex(isin);
        Locator cell = page.locator(TickingCellHelper.buildCellSelector(colId, rowIndex));
        assertThat(cell).containsText(
                Pattern.compile("[0-9]+[.][0-9]+"),
                new com.microsoft.playwright.assertions.LocatorAssertions
                        .ContainsTextOptions().setTimeout(5_000));
    }

    /**
     * Asserts that the given column cell for the row identified by {@code isin} is blank.
     */
    public void assertCellBlank(String colId, String isin) {
        int rowIndex = findRowIndex(isin);
        Locator cell = page.locator(TickingCellHelper.buildCellSelector(colId, rowIndex));
        assertThat(cell).hasText(
                "",
                new com.microsoft.playwright.assertions.LocatorAssertions
                        .HasTextOptions().setTimeout(5_000));
    }

    // ── Ticking-cell assertions ───────────────────────────────────────────────

    /**
     * Blocks until the cell at {@code colId / rowIndex} changes value.
     * Safe to call on live-ticking price cells without {@code Thread.sleep()}.
     */
    public void waitForCellToChange(String colId, int rowIndex, Duration timeout) {
        tickingCells.waitForCellUpdate(colId, rowIndex, timeout);
    }

    /**
     * Asserts (with Playwright auto-retry) that the cell text matches {@code pattern}.
     * Uses {@code containsText(Pattern)} so ticking cells get retried automatically.
     */
    public void assertCellMatchesPattern(String colId, int rowIndex, String pattern) {
        Locator cell = page.locator(TickingCellHelper.buildCellSelector(colId, rowIndex));
        assertThat(cell).containsText(
                Pattern.compile(pattern),
                new com.microsoft.playwright.assertions.LocatorAssertions
                        .ContainsTextOptions().setTimeout(5_000));
    }

    // ── Row selection ─────────────────────────────────────────────────────────

    /**
     * Selects the row identified by {@code isin} (Ctrl+click for multi-select).
     * Idempotent: does nothing if the row is already selected.
     *
     * <p>{@code force:true} bypasses the Playwright stability gate that triggers when
     * live-ticking cells cause the row to be considered "animating" continuously.
     */
    public void selectRowByIsin(String isin) {
        int rowIndex = findRowIndex(isin);
        Locator row = page.locator(
                ".ag-pinned-left-cols-container [row-index='" + rowIndex + "']");

        String classes = row.getAttribute("class");
        boolean alreadySelected = classes != null && classes.contains("ag-row-selected");
        if (!alreadySelected) {
            page.keyboard().down("Control");
            row.click(new Locator.ClickOptions().setForce(true));
            page.keyboard().up("Control");
        }
    }

    // ── Toolbar controls ──────────────────────────────────────────────────────

    /**
     * Sets all four toolbar controls in one call.
     *
     * @param source  reference source: "TW", "CP+", or "CBBT"
     * @param side    reference side: "Bid", "Ask", or "Mid"
     * @param markup  markup offset (as a string, e.g. "0", "0.5", "-0.25")
     * @param units   "c" (cents/price) or "bp" (basis points/spread)
     */
    public void setToolbar(String source, String side, String markup, String units) {
        page.getByLabel(LABEL_SOURCE).selectOption(source);
        page.getByLabel(LABEL_SIDE).selectOption(side);
        page.getByLabel(LABEL_MARKUP).fill(markup);
        if ("c".equals(units))       page.getByLabel(LABEL_UNITS_C).click();
        else if ("bp".equals(units)) page.getByLabel(LABEL_UNITS_BP).click();
    }

    public void pressApply()    { page.getByLabel(LABEL_APPLY).click(); }
    public void pressSend()     { page.getByLabel(LABEL_SEND).click(); }
    public void pressReleasePt(){ page.getByLabel(LABEL_RELEASE).click(); }

    // ── RELEASE PT access control ─────────────────────────────────────────────

    public void assertReleasePtDisabled() {
        assertThat(page.getByLabel(LABEL_RELEASE)).isDisabled();
    }

    public void assertReleasePtEnabled() {
        assertThat(page.getByLabel(LABEL_RELEASE)).isEnabled();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Returns the {@code row-index} attribute for the row that has ISIN = {@code isin}.
     *
     * <p>Two-step, scroll-aware approach:
     * <ol>
     *   <li>Scroll to the top of the grid viewport so the search starts from row 0.</li>
     *   <li>Poll with {@code waitForFunction}: if the row is not in the DOM yet,
     *       scroll down one page-height to trigger AG Grid row virtualisation and
     *       retry on the next poll cycle.</li>
     *   <li>Evaluate again after the wait to retrieve the numeric row-index.</li>
     * </ol>
     *
     * <p>Returning a boolean from step 2 (not the row index directly) avoids the
     * JavaScript falsy-zero problem: row index 0 is falsy, so returning it directly
     * would cause {@code waitForFunction} to retry forever when the target is row 0.
     *
     * <p>The probes are from {@code window.agGridProbes.*}, injected by
     * {@code b-bot-core}'s {@code PlaywrightManager} via {@code addInitScript}.
     */
    private int findRowIndex(String isin) {
        /* Scroll to top so the search always starts from row 0 */
        page.evaluate("() => window.agGridProbes.scroll.scrollToTop()");

        /* Step 1 — poll until probe finds the row, scrolling down on each miss */
        page.waitForFunction(
                "isin => {" +
                "  var idx = window.agGridProbes.dom.findRowIndexByText('isin', isin);" +
                "  if (typeof idx === 'number' && idx >= 0) return true;" +
                "  /* Row not visible yet — scroll down to load more virtualised rows */" +
                "  if (!window.agGridProbes.scroll.isAtBottom()) {" +
                "    window.agGridProbes.scroll.scrollDown();" +
                "  }" +
                "  return false;" +
                "}",
                isin,
                new Page.WaitForFunctionOptions().setTimeout(ROW_LOOKUP_MS));

        /* Step 2 — retrieve the actual row index */
        Object result = page.evaluate(
                "isin => window.agGridProbes.dom.findRowIndexByText('isin', isin)",
                isin);
        int index = result instanceof Number n ? n.intValue() : -1;
        if (index < 0)
            throw new AssertionError("Row with ISIN '" + isin + "' not found in the blotter grid");
        return index;
    }
}
