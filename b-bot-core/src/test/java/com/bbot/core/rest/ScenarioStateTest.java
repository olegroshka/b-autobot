package com.bbot.core.rest;

import com.bbot.core.exception.BBotConfigException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ScenarioState}.
 *
 * <p>Covers: put/get round-trip, require semantics, reset, resolve token
 * substitution, unresolved tokens left intact, thread isolation.
 */
class ScenarioStateTest {

    @AfterEach
    void cleanup() {
        ScenarioState.reset();
    }

    // ── put / get ─────────────────────────────────────────────────────────────

    @Test
    void putAndGet_roundTrip() {
        ScenarioState.put("inquiry_id", "INQ-001");
        assertThat(ScenarioState.get("inquiry_id")).hasValue("INQ-001");
    }

    @Test
    void get_absentKey_returnsEmpty() {
        assertThat(ScenarioState.get("nonexistent")).isEmpty();
    }

    // ── require ───────────────────────────────────────────────────────────────

    @Test
    void require_presentKey_returnsValue() {
        ScenarioState.put("pt_id", "PT-999");
        assertThat(ScenarioState.require("pt_id")).isEqualTo("PT-999");
    }

    @Test
    void require_absentKey_throwsWithDiagnostic() {
        ScenarioState.put("existing_key", "value");

        assertThatThrownBy(() -> ScenarioState.require("missing_key"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("missing_key")
            .hasMessageContaining("existing_key");
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    void reset_clearsAllKeys() {
        ScenarioState.put("a", "1");
        ScenarioState.put("b", "2");

        ScenarioState.reset();

        assertThat(ScenarioState.get("a")).isEmpty();
        assertThat(ScenarioState.get("b")).isEmpty();
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Test
    void resolve_substitutesMultipleTokens() {
        ScenarioState.put("inquiry_id", "INQ-001");
        ScenarioState.put("pt_id", "PT-999");

        String result = ScenarioState.resolve("/api/inquiry/${inquiry_id}/pt/${pt_id}");
        assertThat(result).isEqualTo("/api/inquiry/INQ-001/pt/PT-999");
    }

    @Test
    void resolve_leavesUnresolvedTokensIntact() {
        ScenarioState.put("known", "value");

        String result = ScenarioState.resolve("${known} and ${missing}");
        assertThat(result).isEqualTo("value and ${missing}");
    }

    @Test
    void resolve_noTokens_returnsOriginal() {
        String result = ScenarioState.resolve("plain text with no tokens");
        assertThat(result).isEqualTo("plain text with no tokens");
    }

    // ── thread isolation ──────────────────────────────────────────────────────

    @Test
    void threadIsolation_independentState() throws Exception {
        ScenarioState.put("key", "main-thread");

        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<String> otherThreadValue = new AtomicReference<>();
        AtomicReference<Boolean> otherThreadHasKey = new AtomicReference<>();

        Thread other = new Thread(() -> {
            otherThreadHasKey.set(ScenarioState.get("key").isPresent());
            ScenarioState.put("key", "other-thread");
            otherThreadValue.set(ScenarioState.require("key"));
            ScenarioState.reset();
            ready.countDown();
        });
        other.start();
        ready.await();

        // Other thread should NOT have seen main thread's value
        assertThat(otherThreadHasKey.get()).isFalse();
        // Other thread had its own value
        assertThat(otherThreadValue.get()).isEqualTo("other-thread");
        // Main thread's value is untouched
        assertThat(ScenarioState.require("key")).isEqualTo("main-thread");
    }
}

