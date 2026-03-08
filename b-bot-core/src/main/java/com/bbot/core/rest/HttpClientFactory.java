package com.bbot.core.rest;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.registry.BBotRegistry;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Centralised {@link HttpClient} factory for b-bot-core.
 *
 * <p>Provides a single, lazily-created, thread-safe {@link HttpClient} instance
 * shared across all REST operations in the framework. The connect timeout is
 * resolved from the active {@link BBotConfig} ({@code b-bot.timeouts.apiResponse},
 * default 10 s).
 *
 * <h2>Why centralise?</h2>
 * <ul>
 *   <li>Eliminates multiple {@code HttpClient.newBuilder()} call sites</li>
 *   <li>Single configuration point for timeouts, connection pooling, etc.</li>
 *   <li>Future-proofed for auth interceptors and retry policies</li>
 * </ul>
 */
public final class HttpClientFactory {

    private static volatile HttpClient INSTANCE;

    private HttpClientFactory() {}

    /**
     * Returns a shared {@link HttpClient} instance with the configured connect timeout.
     * The instance is created lazily (double-checked locking) and reused for the
     * lifetime of the JVM.
     */
    public static HttpClient shared() {
        if (INSTANCE == null) {
            synchronized (HttpClientFactory.class) {
                if (INSTANCE == null) {
                    INSTANCE = HttpClient.newBuilder()
                            .connectTimeout(resolveTimeout())
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Creates a new {@link HttpClient} instance with the specified connect timeout.
     * Use this when you need a dedicated client with custom settings (e.g., health checks
     * with a different timeout).
     *
     * @param connectTimeout the connect timeout for the new client
     * @return a new {@link HttpClient} instance
     */
    public static HttpClient withTimeout(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    /**
     * Resets the shared instance. Call from test teardown to allow a fresh client
     * to be created with potentially different config settings.
     */
    static void reset() {
        INSTANCE = null;
    }

    private static Duration resolveTimeout() {
        try {
            BBotConfig cfg = BBotRegistry.getConfig();
            if (cfg != null && cfg.hasPath("b-bot.timeouts.apiResponse"))
                return cfg.getTimeout("b-bot.timeouts.apiResponse");
        } catch (Exception ignored) { /* registry not yet initialised */ }
        return Duration.ofSeconds(10);
    }
}

