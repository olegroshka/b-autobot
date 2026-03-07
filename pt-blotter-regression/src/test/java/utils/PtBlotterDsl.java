package utils;

import com.bbot.core.registry.AppContext;
import com.microsoft.playwright.Page;

/**
 * Minimal DSL for the real PT-Blotter.
 *
 * <p>Extend with methods matching the real system's UI interactions.
 * URLs and users are injected via {@link AppContext} from the active
 * environment config — zero hardcoded values.
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
     * URL pattern: {@code {webUrl}?user={user}&configUrl={configServiceApiBase}}
     */
    public void openBlotter(String user) {
        String configUrl = ctx.getOtherAppApiBase("config-service");
        String url = ctx.getWebUrl() + "?user=" + user
                + (configUrl != null ? "&configUrl=" + configUrl : "");
        page.navigate(url);
        page.waitForSelector(".ag-center-cols-container [row-index='0']");
    }

    /** Opens the blotter as the default trader user. */
    public void openBlotter() {
        String trader = ctx.getUser("trader").orElse("doej");
        openBlotter(trader);
    }

    /** Asserts the AG Grid centre container has at least one rendered row. */
    public void assertGridRendered() {
        page.waitForSelector(".ag-center-cols-container [row-index='0']");
    }

    /** Returns the current page title. */
    public String getTitle() {
        return page.title();
    }
}
