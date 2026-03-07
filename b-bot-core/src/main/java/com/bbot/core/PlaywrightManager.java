package com.bbot.core;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.registry.BBotRegistry;
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
 * in the consuming module's {@code Hooks} class).
 *
 * <h2>Configuration</h2>
 * All browser settings are read from the active {@link BBotConfig} via
 * {@link BBotRegistry#getConfig()}. Override any default in your environment conf
 * or on the command line:
 * <pre>
 *   b-bot.browser.headless = false      # -Db-bot.browser.headless=false
 *   b-bot.browser.type     = firefox    # -Db-bot.browser.type=firefox
 *   b-bot.browser.viewport.width  = 1440
 *   b-bot.browser.viewport.height = 900
 * </pre>
 *
 * <p>Falls back to legacy system properties ({@code HEADLESS}, {@code BROWSER})
 * when the registry has not yet been initialised, so existing suites that call
 * {@code initBrowser()} before {@code BBotRegistry.initialize()} continue to work.
 */
public final class PlaywrightManager {

    private static final ThreadLocal<Playwright>     PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser>        BROWSER    = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT    = new ThreadLocal<>();
    private static final ThreadLocal<Page>           PAGE       = new ThreadLocal<>();

    private PlaywrightManager() {}

    /**
     * Creates a Playwright instance and launches a browser.
     * Browser type and headless mode are resolved from {@link BBotConfig}
     * (preferred) or legacy {@code HEADLESS} / {@code BROWSER} system properties
     * (fallback for suites that do not use {@link BBotRegistry}).
     */
    public static void initBrowser() {
        BBotConfig cfg = BBotRegistry.getConfig();

        boolean headless = cfg != null
                ? cfg.getBoolean("b-bot.browser.headless")
                : Boolean.parseBoolean(System.getProperty("HEADLESS", "true"));

        String browserType = cfg != null
                ? cfg.getString("b-bot.browser.type").toLowerCase()
                : System.getProperty("BROWSER", "chromium").toLowerCase();

        Playwright pw = Playwright.create();
        PLAYWRIGHT.set(pw);

        BrowserType type = switch (browserType) {
            case "firefox" -> pw.firefox();
            case "webkit"  -> pw.webkit();
            default        -> pw.chromium();
        };

        BROWSER.set(type.launch(new BrowserType.LaunchOptions().setHeadless(headless)));
    }

    /**
     * Creates a new {@link BrowserContext} and {@link Page} for the current scenario.
     * Viewport dimensions are resolved from {@link BBotConfig}; the agGridProbes
     * bundle is injected as an init script so {@code window.agGridProbes} is
     * available on every page before any page script runs.
     */
    public static void initContext() {
        BBotConfig cfg = BBotRegistry.getConfig();

        int viewportW = cfg != null && cfg.hasPath("b-bot.browser.viewport.width")
                ? cfg.raw().getInt("b-bot.browser.viewport.width")  : 1920;
        int viewportH = cfg != null && cfg.hasPath("b-bot.browser.viewport.height")
                ? cfg.raw().getInt("b-bot.browser.viewport.height") : 1080;

        BrowserContext ctx = BROWSER.get().newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(viewportW, viewportH));
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
