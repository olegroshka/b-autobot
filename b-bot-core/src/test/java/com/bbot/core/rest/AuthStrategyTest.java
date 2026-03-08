package com.bbot.core.rest;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AuthStrategy} and its implementations.
 */
class AuthStrategyTest {

    @Test
    void none_addsNoHeaders() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost/test"));

        AuthStrategy.none().apply(builder);

        HttpRequest req = builder.GET().build();
        assertThat(req.headers().map()).doesNotContainKey("Authorization");
    }

    @Test
    void bearer_addsAuthorizationHeader() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost/test"));

        AuthStrategy.bearer("my-secret-token").apply(builder);

        HttpRequest req = builder.GET().build();
        assertThat(req.headers().firstValue("Authorization"))
                .isPresent()
                .hasValue("Bearer my-secret-token");
    }

    @Test
    void bearer_nullToken_throws() {
        assertThatThrownBy(() -> AuthStrategy.bearer(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    @Test
    void bearer_blankToken_throws() {
        assertThatThrownBy(() -> AuthStrategy.bearer("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    @Test
    void customLambda_addsCustomHeader() {
        AuthStrategy apiKey = builder -> builder.header("X-API-Key", "abc123");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost/test"));
        apiKey.apply(builder);

        HttpRequest req = builder.GET().build();
        assertThat(req.headers().firstValue("X-API-Key"))
                .isPresent()
                .hasValue("abc123");
    }
}

