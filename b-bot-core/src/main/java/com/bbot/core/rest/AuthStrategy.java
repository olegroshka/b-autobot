package com.bbot.core.rest;

import java.net.http.HttpRequest;
import java.nio.file.Path;

/**
 * Strategy interface for applying authentication to outbound HTTP requests.
 *
 * <p>Implementations add authentication headers (or other credentials) to an
 * {@link HttpRequest.Builder} before the request is sent. The strategy is
 * applied transparently by {@link RestProbe} on every request.
 *
 * <h2>Built-in implementations</h2>
 * <ul>
 *   <li>{@link NoAuth} — default, adds nothing</li>
 *   <li>{@link BearerTokenAuth} — adds {@code Authorization: Bearer {token}}</li>
 * </ul>
 *
 * <h2>Custom implementations</h2>
 * <pre>{@code
 * AuthStrategy apiKey = builder -> builder.header("X-API-Key", "my-secret-key");
 * RestProbe probe = RestProbe.builder("blotter")
 *     .apiBase(ctx.getApiBaseUrl())
 *     .auth(apiKey)
 *     .build();
 * }</pre>
 */
@FunctionalInterface
public interface AuthStrategy {

    /**
     * Applies authentication to the given request builder.
     *
     * @param builder the HTTP request builder to modify
     */
    void apply(HttpRequest.Builder builder);

    // ── Built-in implementations ──────────────────────────────────────────────

    /**
     * No-op authentication strategy. This is the default — no headers are added.
     */
    static AuthStrategy none() {
        return NoAuth.INSTANCE;
    }

    /**
     * Bearer token authentication: adds {@code Authorization: Bearer {token}}.
     *
     * <p>Typical usage with config:
     * <pre>{@code
     * String token = System.getenv("B_BOT_UAT_TOKEN");
     * AuthStrategy auth = AuthStrategy.bearer(token);
     * }</pre>
     *
     * @param token the bearer token (without the "Bearer " prefix)
     */
    static AuthStrategy bearer(String token) {
        return new BearerTokenAuth(token);
    }

    /**
     * Creates an {@code AuthStrategy} from a Playwright storageState JSON file.
     *
     * <p>Extracts cookies and/or Bearer tokens saved by
     * {@link com.bbot.core.auth.SsoAuthManager} and injects them into every
     * outbound HTTP request.
     *
     * @param storageStatePath path to the Playwright storageState JSON file
     * @return an auth strategy that injects cookies and/or Bearer token
     * @throws IllegalArgumentException if the file is missing or unparseable
     */
    static AuthStrategy fromStorageState(Path storageStatePath) {
        return new StorageStateAuth(storageStatePath);
    }
}

