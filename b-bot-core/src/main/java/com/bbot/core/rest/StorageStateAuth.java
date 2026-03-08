package com.bbot.core.rest;

import com.bbot.core.auth.SsoAuthManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AuthStrategy} implementation that extracts authentication state from
 * a Playwright {@code storageState} JSON file and injects it into HTTP requests.
 *
 * <p>Supports two sources of credentials from the storageState file:
 * <ul>
 *   <li><b>Bearer token</b> — if the file contains a {@code b-bot-access-token}
 *       localStorage entry (created by {@code clientCredentials} mode), it is
 *       injected as an {@code Authorization: Bearer} header.</li>
 *   <li><b>Cookies</b> — all cookies from the storageState are injected as a
 *       {@code Cookie} header (for interactive/auto modes where auth relies on
 *       session cookies set by the SSO gateway).</li>
 * </ul>
 *
 * <p>If both are present, both headers are sent — the server will use whichever
 * it expects.
 *
 * @see SsoAuthManager
 * @see AuthStrategy#fromStorageState(Path)
 */
final class StorageStateAuth implements AuthStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(StorageStateAuth.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String bearerToken;
    private final List<CookieEntry> cookies;

    /**
     * Parses a Playwright storageState JSON file and extracts auth credentials.
     *
     * @param storageStatePath path to the storageState JSON file
     * @throws IllegalArgumentException if the file cannot be read or parsed
     */
    StorageStateAuth(Path storageStatePath) {
        if (storageStatePath == null || !Files.exists(storageStatePath)) {
            throw new IllegalArgumentException(
                    "StorageStateAuth: storageState file does not exist: " + storageStatePath);
        }

        String token;
        List<CookieEntry> parsedCookies = new ArrayList<>();

        try {
            JsonNode root = MAPPER.readTree(storageStatePath.toFile());

            // Extract Bearer token from localStorage (clientCredentials mode)
            token = SsoAuthManager.readAccessToken(storageStatePath);

            // Extract cookies
            JsonNode cookiesNode = root.get("cookies");
            if (cookiesNode != null && cookiesNode.isArray()) {
                for (JsonNode cookie : cookiesNode) {
                    String name = cookie.path("name").asText("");
                    String value = cookie.path("value").asText("");
                    if (!name.isEmpty()) {
                        parsedCookies.add(new CookieEntry(name, value));
                    }
                }
            }

            LOG.debug("StorageStateAuth loaded — bearerToken={}, cookies={}",
                    token != null ? "present" : "absent", parsedCookies.size());

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "StorageStateAuth: failed to parse storageState file: "
                    + storageStatePath + " — " + e.getMessage(), e);
        }

        this.bearerToken = token;
        this.cookies = List.copyOf(parsedCookies);
    }

    @Override
    public void apply(HttpRequest.Builder builder) {
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (!cookies.isEmpty()) {
            String cookieHeader = cookies.stream()
                    .map(c -> c.name + "=" + c.value)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            if (!cookieHeader.isEmpty()) {
                builder.header("Cookie", cookieHeader);
            }
        }
    }

    /** Returns {@code true} if a Bearer token was found in the storageState. */
    boolean hasBearerToken() {
        return bearerToken != null;
    }

    /** Returns the number of cookies extracted from the storageState. */
    int cookieCount() {
        return cookies.size();
    }

    // -- Internal record for cookie name/value pairs ─────────────────────────

    private record CookieEntry(String name, String value) {}
}

