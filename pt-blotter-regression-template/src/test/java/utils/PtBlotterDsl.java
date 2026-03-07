package utils;

import com.bbot.core.registry.AppContext;
import com.microsoft.playwright.Page;

/**
 * Domain-Specific Language (DSL) for the PT-Blotter UI and REST API.
 *
 * <h2>What this class is</h2>
 * The DSL is the only layer allowed to call Playwright directly. Step definitions
 * delegate entirely to DSL methods -- they never hold a {@link Page} reference.
 * This separation keeps scenarios readable and keeps all locator knowledge in one place.
 *
 * <h2>Template customisation points</h2>
 * <ul>
 *   <li>Rename this class and its package to match your application.</li>
 *   <li>Replace {@code openBlotter} with your own navigation methods.</li>
 *   <li>Add one method per observable user action or assertion your scenarios need.</li>
 *   <li>Use {@link AppContext#getWebUrl()} / {@link AppContext#getApiBaseUrl()} instead
 *       of hardcoded URLs -- the active environment config supplies the correct values.</li>
 *   <li>Use {@link AppContext#getUser(String)} to look up named users from config
 *       ({@code b-bot.apps.blotter.users.trader = doej}).</li>
 * </ul>
 *
 * <h2>Extending for your application</h2>
 * <pre>{@code
 * // Navigate to a specific view
 * public void openDashboard() {
 *     page.navigate(ctx.getWebUrl() + "dashboard");
 *     page.waitForSelector(".dashboard-loaded");
 * }
 *
 * // REST call through the same browser context (shares cookies / auth)
 * public TradeDto submitTrade(TradeRequest req) {
 *     APIResponse resp = page.request().post(
 *         ctx.getApiBaseUrl() + "/api/trades",
 *         RequestOptions.create().setData(req));
 *     assertThat(resp.status()).isEqualTo(201);
 *     return mapper.readValue(resp.body(), TradeDto.class);
 * }
 * }</pre>
 */
public final class PtBlotterDsl {

    private final Page       page;
    private final AppContext ctx;

    public PtBlotterDsl(Page page, AppContext ctx) {
        this.page = page;
        this.ctx  = ctx;
    }

    /**
     * Navigates to the blotter as the given user and waits for the grid to render.
     *
     * <p>The URL is assembled from environment config -- adapt the query-string params
     * to match whatever your real application expects.
     */
    public void openBlotter(String user) {
        String configUrl = ctx.getOtherAppApiBase("config-service");
        String url = ctx.getWebUrl() + "?user=" + user
                + (configUrl != null ? "&configUrl=" + configUrl : "");
        page.navigate(url);
        page.waitForSelector(".ag-center-cols-container [row-index='0']");
    }

    /**
     * Opens the blotter as the default trader declared in config:
     * {@code b-bot.apps.blotter.users.trader}.
     */
    public void openBlotter() {
        String trader = ctx.getUser("trader").orElse("doej");
        openBlotter(trader);
    }

    /**
     * Asserts the AG Grid centre container has at least one rendered row.
     *
     * <p>Adapt the selector to the root element of your own grid / table / component.
     */
    public void assertGridRendered() {
        page.waitForSelector(".ag-center-cols-container [row-index='0']");
    }

    /** Returns the current page title. */
    public String getTitle() {
        return page.title();
    }
}
