package com.bbot.core.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BBotAuthException}.
 */
class BBotAuthExceptionTest {

    @Test
    void extendsRuntimeException() {
        assertThat(BBotAuthException.class.getSuperclass()).isEqualTo(BBotException.class);
        assertThat(new BBotAuthException("test")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void messageOnly() {
        BBotAuthException ex = new BBotAuthException("SSO session expired");
        assertThat(ex.getMessage()).isEqualTo("SSO session expired");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCause() {
        IOException cause = new IOException("network error");
        BBotAuthException ex = new BBotAuthException("Token request failed", cause);
        assertThat(ex.getMessage()).isEqualTo("Token request failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    // Inner class used as test cause
    private static class IOException extends Exception {
        IOException(String msg) { super(msg); }
    }
}

