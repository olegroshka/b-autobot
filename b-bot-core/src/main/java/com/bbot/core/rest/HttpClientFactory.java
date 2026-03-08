package com.bbot.core.rest;

import com.bbot.core.config.BBotConfig;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Centralised {@link HttpClient} factory for b-bot-core.
 *
 * <p>Provides two clean creation strategies:
 * <ul>
 *   <li>{@link #shared()} — returns a lazily-created, thread-safe singleton
 *       with a default 10 s connect timeout. Suitable for most REST operations.</li>
 *   <li>{@link #withTimeout(Duration)} — creates a dedicated client with a
 *       custom connect timeout. Use for health checks or other calls that need
 *       different timing.</li>
 * </ul>
 *
 * <p>No dependency on the static registry — timeouts are either the default
 * or supplied explicitly by the caller.
 */
@SuppressWarnings("unused")
public final class HttpClientFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static volatile HttpClient INSTANCE;

    private HttpClientFactory() {}

    /**
     * Returns a shared {@link HttpClient} instance with a default 10 s connect
     * timeout. The instance is created lazily (double-checked locking) and
     * reused for the lifetime of the JVM.
     */
    public static HttpClient shared() {
        if (INSTANCE == null) {
            synchronized (HttpClientFactory.class) {
                if (INSTANCE == null) {
                    INSTANCE = HttpClient.newBuilder()
                            .connectTimeout(DEFAULT_TIMEOUT)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Creates a new {@link HttpClient} instance with the specified connect timeout.
     * Use this when you need a dedicated client with custom settings (e.g., health
     * checks with a shorter timeout).
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
     * Creates a new {@link HttpClient} with the connect timeout resolved from
     * the given config ({@code b-bot.timeouts.apiResponse}), falling back to
     * 10 s if the key is absent.
     *
     * @param config the active configuration
     * @return a new {@link HttpClient} instance
     */
    public static HttpClient fromConfig(BBotConfig config) {
        Duration timeout = DEFAULT_TIMEOUT;
        if (config != null && config.hasPath("b-bot.timeouts.apiResponse")) {
            timeout = config.getTimeout("b-bot.timeouts.apiResponse");
        }
        return withTimeout(timeout);
    }

    /**
     * Closes and resets the shared {@link HttpClient} instance.
     * Call from {@code @AfterAll} to release resources cleanly.
     */
    public static void shutdown() {
        synchronized (HttpClientFactory.class) {
            if (INSTANCE != null) {
                INSTANCE.close();
                INSTANCE = null;
            }
        }
    }

    /**
     * Resets the shared instance. Call from test teardown to allow a fresh
     * client to be created.
     */
    static void reset() {
        shutdown();
    }
}
