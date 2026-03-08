package com.bbot.core.rest;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RetryPolicy}.
 */
class RetryPolicyTest {

    @Test
    void none_hasZeroRetries() {
        assertThat(RetryPolicy.NONE.maxRetries()).isZero();
        assertThat(RetryPolicy.NONE.initialDelayMs()).isZero();
        assertThat(RetryPolicy.NONE.retryableStatuses()).isEmpty();
    }

    @Test
    void none_shouldRetry_alwaysFalse() {
        assertThat(RetryPolicy.NONE.shouldRetry(500)).isFalse();
        assertThat(RetryPolicy.NONE.shouldRetry(502)).isFalse();
        assertThat(RetryPolicy.NONE.shouldRetry(200)).isFalse();
    }

    @Test
    void serverErrors_retries502_503_504() {
        RetryPolicy policy = RetryPolicy.serverErrors(3, 500);

        assertThat(policy.maxRetries()).isEqualTo(3);
        assertThat(policy.initialDelayMs()).isEqualTo(500);
        assertThat(policy.shouldRetry(502)).isTrue();
        assertThat(policy.shouldRetry(503)).isTrue();
        assertThat(policy.shouldRetry(504)).isTrue();
        assertThat(policy.shouldRetry(500)).isFalse();
        assertThat(policy.shouldRetry(200)).isFalse();
        assertThat(policy.shouldRetry(404)).isFalse();
    }

    @Test
    void delayMs_exponentialBackoff() {
        RetryPolicy policy = new RetryPolicy(3, 500, Set.of(502));

        assertThat(policy.delayMs(1)).isEqualTo(500);    // 500 * 2^0
        assertThat(policy.delayMs(2)).isEqualTo(1000);   // 500 * 2^1
        assertThat(policy.delayMs(3)).isEqualTo(2000);   // 500 * 2^2
    }

    @Test
    void customPolicy_arbitraryStatuses() {
        RetryPolicy policy = new RetryPolicy(2, 100, Set.of(429, 503));

        assertThat(policy.shouldRetry(429)).isTrue();
        assertThat(policy.shouldRetry(503)).isTrue();
        assertThat(policy.shouldRetry(500)).isFalse();
    }
}

