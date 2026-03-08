package com.bbot.core.rest;

import java.net.http.HttpRequest;

/**
 * No-op authentication — adds nothing to the request.
 * This is the default strategy used by {@link RestProbe}.
 */
final class NoAuth implements AuthStrategy {

    static final NoAuth INSTANCE = new NoAuth();

    private NoAuth() {}

    @Override
    public void apply(HttpRequest.Builder builder) {
        // No authentication — intentional no-op
    }
}

