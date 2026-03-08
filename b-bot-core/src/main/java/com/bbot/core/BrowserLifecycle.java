package com.bbot.core;

import com.microsoft.playwright.Page;

/**
 * Abstraction for browser lifecycle management in BDD test scenarios.
 *
 * <p>The default implementation is {@link PlaywrightManager}, which uses
 * thread-local Playwright instances. Consumers may provide mock implementations
 * for unit testing hooks without a real browser.
 *
 * <h2>Lifecycle order</h2>
 * <ol>
 *   <li>{@link #initBrowser()} — once per test thread ({@code @BeforeAll})</li>
 *   <li>{@link #initContext()} — once per scenario ({@code @Before})</li>
 *   <li>{@link #getPage()} — during scenario execution</li>
 *   <li>{@link #closeContext()} — after each scenario ({@code @After})</li>
 *   <li>{@link #closeBrowser()} — once per test thread ({@code @AfterAll})</li>
 * </ol>
 *
 * @see PlaywrightManager
 */
public interface BrowserLifecycle {

    /** Creates and launches a browser instance for the current thread. */
    void initBrowser();

    /** Creates a new browser context and page for the current scenario. */
    void initContext();

    /** Returns the current scenario's page. */
    Page getPage();

    /** Closes the current scenario's context and page. */
    void closeContext();

    /** Closes the browser and Playwright instance for the current thread. */
    void closeBrowser();
}

