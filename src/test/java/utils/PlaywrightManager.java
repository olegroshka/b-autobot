package utils;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Thread-local Playwright lifecycle manager for Cucumber scenarios.
 *
 * <p>One {@link Playwright} + {@link Browser} per test thread; one fresh
 * {@link BrowserContext} + {@link Page} per scenario (via Before/After hooks
 * in {@code stepdefs.Hooks}).
 */
public final class PlaywrightManager {

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser>    BROWSER    = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page>       PAGE       = new ThreadLocal<>();

    private PlaywrightManager() {}

    public static void initBrowser() {
        boolean headless = Boolean.parseBoolean(System.getProperty("HEADLESS", "true"));
        String  browser  = System.getProperty("BROWSER", "chromium").toLowerCase();

        Playwright pw = Playwright.create();
        PLAYWRIGHT.set(pw);

        BrowserType type = switch (browser) {
            case "firefox" -> pw.firefox();
            case "webkit"  -> pw.webkit();
            default        -> pw.chromium();
        };

        BROWSER.set(type.launch(new BrowserType.LaunchOptions().setHeadless(headless)));
    }

    public static void initContext() {
        BrowserContext ctx = BROWSER.get().newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(1920, 1080));
        // Inject the AG Grid probes bundle so window.agGridProbes is available
        // on every page opened in this context, before any page script runs.
        ctx.addInitScript(ProbesLoader.load());
        CONTEXT.set(ctx);
        PAGE.set(ctx.newPage());
    }

    public static Page getPage() {
        return PAGE.get();
    }

    public static void closeContext() {
        if (PAGE.get()    != null) PAGE.get().close();
        if (CONTEXT.get() != null) CONTEXT.get().close();
        PAGE.remove();
        CONTEXT.remove();
    }

    public static void closeBrowser() {
        if (BROWSER.get()    != null) BROWSER.get().close();
        if (PLAYWRIGHT.get() != null) PLAYWRIGHT.get().close();
        BROWSER.remove();
        PLAYWRIGHT.remove();
    }
}
