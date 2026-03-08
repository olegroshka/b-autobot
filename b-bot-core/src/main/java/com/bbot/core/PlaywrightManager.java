package com.bbot.core;

import com.bbot.core.auth.SsoAuthConfig;
import com.bbot.core.auth.SsoAuthManager;
import com.bbot.core.config.BBotConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thread-local Playwright lifecycle manager for Cucumber scenarios.
 *
 * <p>One {@link Playwright} + {@link Browser} per test thread; one fresh
 * {@link BrowserContext} + {@link Page} per scenario (via Before/After hooks
 * in the consuming module's {@code Hooks} class).
 *
 * <p>Implements {@link BrowserLifecycle} with instance methods that delegate
 * to shared thread-local state.
 *
 * <h2>Configuration</h2>
 * All browser settings are read from the injected {@link BBotConfig}.
 * <pre>
 *   b-bot.browser.headless = false
 *   b-bot.browser.type     = firefox
 *   b-bot.browser.viewport.width  = 1440
 *   b-bot.browser.viewport.height = 900
 * </pre>
 */
public final class PlaywrightManager implements BrowserLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(PlaywrightManager.class);

    private static final ThreadLocal<Playwright>     PLAYWRIGHT       = new ThreadLocal<>();
    private static final ThreadLocal<Browser>        BROWSER          = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext>  CONTEXT          = new ThreadLocal<>();
    private static final ThreadLocal<Page>            PAGE             = new ThreadLocal<>();
    private static final ThreadLocal<Boolean>         TRACING_ACTIVE   = ThreadLocal.withInitial(() -> false);

    private final BBotConfig config;

    /**
     * Creates a PlaywrightManager with the given configuration.
     *
     * @param config the active configuration (must not be {@code null})
     */
    public PlaywrightManager(BBotConfig config) {
        this.config = config;
    }

    @Override
    public void initBrowser() {
        boolean headless = config.getBoolean("b-bot.browser.headless");
        String browserType = config.getString("b-bot.browser.type").toLowerCase();

        Playwright pw = Playwright.create();
        PLAYWRIGHT.set(pw);

        BrowserType type = switch (browserType) {
            case "firefox" -> pw.firefox();
            case "webkit"  -> pw.webkit();
            default        -> pw.chromium();
        };

        BROWSER.set(type.launch(new BrowserType.LaunchOptions().setHeadless(headless)));
        LOG.info("Browser launched: type={}, headless={}", browserType, headless);
    }

    @Override
    public void initContext() {
        int viewportW = config.hasPath("b-bot.browser.viewport.width")
                ? config.raw().getInt("b-bot.browser.viewport.width")  : 1920;
        int viewportH = config.hasPath("b-bot.browser.viewport.height")
                ? config.raw().getInt("b-bot.browser.viewport.height") : 1080;

        Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                .setViewportSize(viewportW, viewportH);

        // Inject saved SSO auth state if available
        SsoAuthConfig authConfig = SsoAuthConfig.from(config);
        Path storagePath = SsoAuthManager.getStorageStatePath(authConfig);
        if (storagePath != null && Files.exists(storagePath)) {
            ctxOpts.setStorageStatePath(storagePath);
            LOG.info("Browser context created with SSO storageState: {}", storagePath);
        }

        BrowserContext ctx = BROWSER.get().newContext(ctxOpts);
        ctx.addInitScript(ProbesLoader.load());
        CONTEXT.set(ctx);
        PAGE.set(ctx.newPage());

        // Start Playwright tracing if enabled
        boolean tracingEnabled = config.hasPath("b-bot.tracing.enabled")
                && config.getBoolean("b-bot.tracing.enabled");
        if (tracingEnabled) {
            ctx.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true));
            TRACING_ACTIVE.set(true);
            String outputDir = config.hasPath("b-bot.tracing.outputDir")
                    ? config.getString("b-bot.tracing.outputDir")
                    : "target/playwright-traces";
            LOG.info("Playwright tracing started — outputDir={}", outputDir);
        }

        LOG.debug("Browser context created: viewport={}x{}, tracing={}", viewportW, viewportH, tracingEnabled);
    }

    @Override
    public Page getPage() {
        return PAGE.get();
    }

    @Override
    public void closeContext() {
        // Save trace before closing context
        if (Boolean.TRUE.equals(TRACING_ACTIVE.get()) && CONTEXT.get() != null) {
            try {
                String outputDir = config.hasPath("b-bot.tracing.outputDir")
                        ? config.getString("b-bot.tracing.outputDir")
                        : "target/playwright-traces";
                Path dir = Path.of(outputDir);
                Files.createDirectories(dir);
                String fileName = "trace-" + Thread.currentThread().getName()
                        + "-" + System.currentTimeMillis() + ".zip";
                Path tracePath = dir.resolve(fileName);
                CONTEXT.get().tracing().stop(new Tracing.StopOptions().setPath(tracePath));
                LOG.info("Playwright trace saved: {}", tracePath);
            } catch (Exception e) {
                LOG.warn("Failed to save Playwright trace: {}", e.getMessage());
            }
            TRACING_ACTIVE.set(false);
        }

        if (PAGE.get()    != null) PAGE.get().close();
        if (CONTEXT.get() != null) CONTEXT.get().close();
        PAGE.remove();
        CONTEXT.remove();
        LOG.debug("Browser context closed");
    }

    @Override
    public void closeBrowser() {
        if (BROWSER.get()    != null) BROWSER.get().close();
        if (PLAYWRIGHT.get() != null) PLAYWRIGHT.get().close();
        BROWSER.remove();
        PLAYWRIGHT.remove();
        LOG.debug("Browser closed");
    }
}
