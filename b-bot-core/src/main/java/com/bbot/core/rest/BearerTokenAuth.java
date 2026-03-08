package com.bbot.core.rest;

import java.net.http.HttpRequest;

/**
 * Bearer token authentication — adds {@code Authorization: Bearer {token}} to every request.
 *
 * <p>The token is typically sourced from an environment variable or config:
 * <pre>{@code
 * // From environment variable
 * AuthStrategy auth = AuthStrategy.bearer(System.getenv("B_BOT_UAT_TOKEN"));
 *
 * // From HOCON config
 * AuthStrategy auth = AuthStrategy.bearer(cfg.getString("b-bot.auth.token"));
 * }</pre>
 */
final class BearerTokenAuth implements AuthStrategy {

    private final String token;

    BearerTokenAuth(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                "Bearer token must not be null or blank. " +
                "Check your environment variable or b-bot.auth.token config.");
        }
        this.token = token;
    }

    @Override
    public void apply(HttpRequest.Builder builder) {
        builder.header("Authorization", "Bearer " + token);
    }
}

