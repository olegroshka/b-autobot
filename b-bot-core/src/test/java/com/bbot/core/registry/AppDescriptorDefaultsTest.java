package com.bbot.core.registry;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AppDescriptorDefaultsTest {

    /** Minimal descriptor that only implements the required methods. */
    private static final AppDescriptor<String> MINIMAL = new AppDescriptor<>() {
        @Override public String name() { return "minimal"; }
        @Override public Set<ComponentType> componentTypes() { return Set.of(ComponentType.REST_API); }
        @Override public DslFactory<String> dslFactory() { return (ctx, page) -> "dsl"; }
    };

    @Test
    void healthCheckPathIsEmptyByDefault() {
        assertThat(MINIMAL.healthCheckPath()).isEmpty();
    }

    @Test
    void versionPathIsEmptyByDefault() {
        assertThat(MINIMAL.versionPath()).isEmpty();
    }
}
