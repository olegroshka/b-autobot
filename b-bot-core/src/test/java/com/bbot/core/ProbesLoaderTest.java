package com.bbot.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProbesLoader}.
 *
 * <p>Verifies the JS probe bundle is loadable from the classpath, contains
 * the expected namespace, and is cached after first load.
 */
class ProbesLoaderTest {

    @Test
    void load_returnsNonEmptyContent() {
        String bundle = ProbesLoader.load();
        assertThat(bundle).isNotNull().isNotEmpty();
    }

    @Test
    void load_containsAgGridProbes() {
        String bundle = ProbesLoader.load();
        assertThat(bundle).contains("agGridProbes");
    }

    @Test
    void load_cachedOnSecondCall() {
        String first  = ProbesLoader.load();
        String second = ProbesLoader.load();
        // Same String reference — cached, not re-read from classpath
        assertThat(first).isSameAs(second);
    }
}

