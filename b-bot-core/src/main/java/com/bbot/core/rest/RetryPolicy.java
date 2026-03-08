package com.bbot.core.rest;

import java.util.Set;

/**
 * Configuration for automatic retry of failed HTTP requests.
 *
 * <p>When a request fails with a status code in {@link #retryableStatuses()},
 * the REST client will retry up to {@link #maxRetries()} times with exponential
 * backoff starting at {@link #initialDelayMs()} milliseconds.
 *
 * <h2>Backoff formula</h2>
 * <pre>
 *   delay(attempt) = initialDelayMs × 2^(attempt - 1)
 *   attempt 1: initialDelayMs
 *   attempt 2: initialDelayMs × 2
 *   attempt 3: initialDelayMs × 4
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Retry 502/503/504 up to 3 times, starting at 500ms
 * RetryPolicy policy = new RetryPolicy(3, 500, Set.of(502, 503, 504));
 *
 * // No retry (default)
 * RetryPolicy.NONE
 * }</pre>
 *
 * @param maxRetries        maximum number of retry attempts (0 = no retry)
 * @param initialDelayMs    initial delay before the first retry (milliseconds)
 * @param retryableStatuses HTTP status codes that trigger a retry
 */
public record RetryPolicy(int maxRetries, long initialDelayMs, Set<Integer> retryableStatuses) {

    /** No-retry policy — the default for all REST probes. */
    public static final RetryPolicy NONE = new RetryPolicy(0, 0, Set.of());

    /**
     * Convenience factory for common server-error retry scenarios.
     * Retries on 502, 503, and 504 with the specified attempts and delay.
     *
     * @param maxRetries     maximum retry attempts
     * @param initialDelayMs initial delay in milliseconds
     * @return a retry policy targeting gateway/unavailable errors
     */
    public static RetryPolicy serverErrors(int maxRetries, long initialDelayMs) {
        return new RetryPolicy(maxRetries, initialDelayMs, Set.of(502, 503, 504));
    }

    /**
     * Returns whether the given HTTP status code should trigger a retry.
     */
    public boolean shouldRetry(int statusCode) {
        return retryableStatuses.contains(Integer.valueOf(statusCode));
    }

    /**
     * Calculates the delay for the given attempt number (1-based).
     *
     * @param attempt the attempt number (1 = first retry)
     * @return delay in milliseconds
     */
    public long delayMs(int attempt) {
        return initialDelayMs * (1L << (attempt - 1));
    }
}

