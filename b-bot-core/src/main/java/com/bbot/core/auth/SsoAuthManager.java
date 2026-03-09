package com.bbot.core.auth;

import com.bbot.core.exception.BBotAuthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Orchestrates SSO / OAuth authentication for Playwright browser sessions
 * and REST API calls.
 *
 * <p>Call {@link #ensureAuthenticated(SsoAuthConfig)} once in {@code @BeforeAll}
 * before any browser contexts or REST probes are created. The method is
 * idempotent: if a valid cached session already exists, it returns immediately.
 *
 * <h2>Supported authentication modes</h2>
 * <ul>
 *   <li><b>NONE</b> -- no-op (default for mock environments)</li>
 *   <li><b>INTERACTIVE</b> -- opens a headed Chromium, navigates to the
 *       SSO login URL, and waits for the user to complete login + MFA.
 *       Saves the resulting browser state to disk.</li>
 *   <li><b>STORAGE_STATE</b> -- loads a previously saved storageState file.
 *       Throws if the file does not exist.</li>
 *   <li><b>AUTO</b> -- tries STORAGE_STATE first; falls back to INTERACTIVE
 *       if the cached session is missing or expired.</li>
 *   <li><b>CLIENT_CREDENTIALS</b> -- performs an OAuth2 client_credentials
 *       grant and synthesises a storageState JSON with the access token.</li>
 * </ul>
 *
 * <p>After this method returns, {@link #getStorageStatePath(SsoAuthConfig)}
 * returns the path to the storageState JSON file (or {@code null} for NONE mode).
 * Pass this path to {@code Browser.NewContextOptions.setStorageStatePath()}
 * when creating Playwright browser contexts.
 *
 * @see SsoAuthConfig
 */
public final class SsoAuthManager {

    private static final Logger LOG = LoggerFactory.getLogger(SsoAuthManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Timeout in seconds for each OAuth2 token HTTP request. */
    static final int OAUTH_REQUEST_TIMEOUT_SECONDS = 30;

    private SsoAuthManager() {}

    /**
     * Ensures that a valid authentication state exists on disk.
     *
     * <p>Must be called once per suite, typically in {@code @BeforeAll},
     * before {@code PlaywrightManager.initBrowser()} or any REST probe calls.
     *
     * @param config the SSO auth configuration
     * @throws BBotAuthException if authentication fails or times out
     */
    public static void ensureAuthenticated(SsoAuthConfig config) {
        config.validate();

        switch (config.mode()) {
            case NONE -> LOG.debug("Auth mode=NONE -- skipping authentication");

            case INTERACTIVE -> {
                LOG.info("Auth mode=INTERACTIVE -- starting interactive login");
                performInteractiveLogin(config);
            }

            case STORAGE_STATE -> {
                if (!config.isStorageStateValid()) {
                    throw new BBotAuthException(
                            "Auth mode=STORAGE_STATE but cached session is missing or expired: "
                            + config.storageStatePath()
                            + "\nRe-run with -Db-bot.auth.mode=interactive to create a new session.");
                }
                LOG.info("Auth mode=STORAGE_STATE -- using cached session: {}",
                        config.storageStatePath());
            }

            case AUTO -> {
                if (config.isStorageStateValid()) {
                    LOG.info("Auth mode=AUTO -- cached session is valid, reusing: {}",
                            config.storageStatePath());
                } else {
                    LOG.warn("Auth mode=AUTO -- cached session expired or missing, starting interactive login...");
                    performInteractiveLogin(config);
                }
            }

            case CLIENT_CREDENTIALS -> {
                LOG.info("Auth mode=CLIENT_CREDENTIALS -- acquiring OAuth token");
                performClientCredentialsGrant(config);
            }
        }
    }

    /**
     * Returns the path to the storageState JSON file that should be injected
     * into {@code Browser.NewContextOptions}, or {@code null} if auth mode
     * is NONE.
     *
     * @param config the SSO auth configuration
     * @return path to storageState file, or null
     */
    public static Path getStorageStatePath(SsoAuthConfig config) {
        if (config.isNone()) {
            return null;
        }
        Path path = config.storageStatePath();
        return Files.exists(path) ? path : null;
    }

    // -- Interactive login (requires a headed browser) ------------------------

    /**
     * Opens a headed Chromium browser, navigates to the SSO login URL,
     * and waits for the user to complete authentication (including MFA).
     *
     * <p>The resulting browser state (cookies + localStorage) is saved to
     * the configured storageState path.
     *
     * <p>This method uses Playwright directly. It creates its own
     * Playwright instance (separate from the test-suite's PlaywrightManager)
     * so that the login browser is fully isolated.
     */
    static void performInteractiveLogin(SsoAuthConfig config) {
        LOG.info("Opening headed browser for SSO login...");
        LOG.info("Login URL: {}", config.loginUrl());
        LOG.info("Approve the MFA request on your device. Timeout: {}", config.loginTimeout());

        // Dynamic import to avoid hard compile dependency on Playwright
        // in environments that only use CLIENT_CREDENTIALS mode.
        try (var pw = com.microsoft.playwright.Playwright.create()) {
            try (var browser = pw.chromium().launch(
                    new com.microsoft.playwright.BrowserType.LaunchOptions()
                            .setHeadless(false))) {
                var ctx = browser.newContext();
                var page = ctx.newPage();

                page.navigate(config.loginUrl());

                waitForLoginSuccess(page, config);

                // Save authenticated state
                Files.createDirectories(config.storageStatePath().getParent());
                ctx.storageState(
                        new com.microsoft.playwright.BrowserContext.StorageStateOptions()
                                .setPath(config.storageStatePath()));

                LOG.info("SSO session saved to: {}", config.storageStatePath());
            }
        } catch (BBotAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new BBotAuthException(
                    "Interactive SSO login failed: " + e.getMessage()
                    + "\nEnsure Playwright browsers are installed: npx playwright install chromium", e);
        }
    }

    /**
     * Waits for the login to succeed using the configured indicator strategy.
     */
    private static void waitForLoginSuccess(
            com.microsoft.playwright.Page page, SsoAuthConfig config) {

        String indicator = config.loginSuccessIndicator();
        long timeoutMs = config.loginTimeout().toMillis();

        if ("pause".equals(indicator)) {
            // Opens the Playwright Inspector -- user clicks "Resume" when done
            LOG.info("Playwright Inspector will open. Complete SSO login, then click 'Resume'.");
            page.pause();

        } else if (indicator.startsWith("urlContains:")) {
            String pattern = indicator.substring("urlContains:".length());
            LOG.info("Waiting for URL to contain: '{}'", pattern);
            page.waitForURL("**" + pattern + "**",
                    new com.microsoft.playwright.Page.WaitForURLOptions()
                            .setTimeout(timeoutMs));

        } else if (indicator.startsWith("element:")) {
            String selector = indicator.substring("element:".length());
            LOG.info("Waiting for element: '{}'", selector);
            page.waitForSelector(selector,
                    new com.microsoft.playwright.Page.WaitForSelectorOptions()
                            .setTimeout(timeoutMs));

        } else {
            throw new BBotAuthException(
                    "Unrecognised loginSuccessIndicator: '" + indicator + "'. "
                    + "Valid formats: pause, urlContains:<pattern>, element:<selector>");
        }
    }

    // -- OAuth2 client_credentials grant (headless CI) ------------------------

    /**
     * Performs an OAuth2 client_credentials grant, then synthesises a
     * Playwright-compatible storageState JSON file containing the access
     * token as a localStorage entry.
     */
    static void performClientCredentialsGrant(SsoAuthConfig config) {
        LOG.info("OAuth2 client_credentials -- tokenUrl: {}", config.tokenUrl());

        String formBody = "grant_type=client_credentials"
                + "&client_id=" + urlEncode(config.clientId())
                + "&client_secret=" + urlEncode(config.clientSecret())
                + (config.scope().isBlank() ? "" : "&scope=" + urlEncode(config.scope()));

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.tokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(OAUTH_REQUEST_TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> resp = client
                    .send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new BBotAuthException(
                        "OAuth client_credentials token request failed: HTTP " + resp.statusCode()
                        + "\nResponse: " + resp.body()
                        + "\nCheck b-bot.auth.tokenUrl, clientId, clientSecret, and scope.");
            }

            String accessToken = parseAccessToken(resp.body());
            saveTokenAsStorageState(config.storageStatePath(), accessToken);
            LOG.info("OAuth token acquired and saved to: {}", config.storageStatePath());

        } catch (Exception e) {
            if (e instanceof BBotAuthException) throw (BBotAuthException) e;
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new BBotAuthException(
                    "OAuth token request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the {@code access_token} field from an OAuth2 token response JSON.
     */
    static String parseAccessToken(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode tokenNode = root.get("access_token");
            if (tokenNode == null || tokenNode.isNull() || tokenNode.asText().isBlank()) {
                throw new BBotAuthException(
                        "OAuth response does not contain 'access_token' field.\nResponse: "
                        + responseBody);
            }
            return tokenNode.asText();
        } catch (Exception e) {
            if (e instanceof BBotAuthException) throw (BBotAuthException) e;
            throw new BBotAuthException(
                    "Failed to parse OAuth token response: " + e.getMessage(), e);
        }
    }

    /**
     * Synthesises a minimal Playwright storageState JSON with the access
     * token stored as a localStorage entry.
     *
     * <p>The format matches Playwright's expected schema:
     * <pre>{@code
     * {
     *   "cookies": [],
     *   "origins": [{
     *     "origin": "https://b-bot-auth",
     *     "localStorage": [
     *       { "name": "b-bot-access-token", "value": "<token>" }
     *     ]
     *   }]
     * }
     * }</pre>
     */
    static void saveTokenAsStorageState(Path path, String accessToken) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.set("cookies", MAPPER.createArrayNode());

            ObjectNode origin = MAPPER.createObjectNode();
            origin.put("origin", "https://b-bot-auth");
            ArrayNode localStorage = MAPPER.createArrayNode();
            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("name", "b-bot-access-token");
            entry.put("value", accessToken);
            localStorage.add(entry);
            origin.set("localStorage", localStorage);

            ArrayNode origins = MAPPER.createArrayNode();
            origins.add(origin);
            root.set("origins", origins);

            Files.createDirectories(path.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);

        } catch (IOException e) {
            throw new BBotAuthException(
                    "Failed to write storageState file: " + path + " -- " + e.getMessage(), e);
        }
    }

    /**
     * Reads the access token from a previously saved storageState JSON file.
     * Returns {@code null} if the file does not contain a {@code b-bot-access-token}
     * localStorage entry (i.e. it was created by interactive login, not client_credentials).
     */
    public static String readAccessToken(Path storageStatePath) {
        if (storageStatePath == null || !Files.exists(storageStatePath)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(storageStatePath.toFile());
            JsonNode origins = root.get("origins");
            if (origins != null && origins.isArray()) {
                for (JsonNode origin : origins) {
                    JsonNode ls = origin.get("localStorage");
                    if (ls != null && ls.isArray()) {
                        for (JsonNode item : ls) {
                            if ("b-bot-access-token".equals(item.path("name").asText())) {
                                return item.path("value").asText(null);
                            }
                        }
                    }
                }
            }
            return null;
        } catch (IOException e) {
            LOG.warn("Could not read storageState file: {}", e.getMessage());
            return null;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

