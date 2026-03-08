package com.bbot.core.rest;

import com.bbot.core.exception.BBotConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ScenarioContext}.
 *
 * <p>Covers: put/get round-trip, require semantics, reset, resolve token
 * substitution, unresolved tokens left intact.
 */
class ScenarioContextTest {

    private ScenarioContext ctx;

    @BeforeEach
    void setup() {
        ctx = new ScenarioContext();
    }

    // ── put / get ─────────────────────────────────────────────────────────────

    @Test
    void putAndGet_roundTrip() {
        ctx.put("inquiry_id", "INQ-001");
        assertThat(ctx.get("inquiry_id")).hasValue("INQ-001");
    }

    @Test
    void get_absentKey_returnsEmpty() {
        assertThat(ctx.get("nonexistent")).isEmpty();
    }

    // ── require ───────────────────────────────────────────────────────────────

    @Test
    void require_presentKey_returnsValue() {
        ctx.put("pt_id", "PT-999");
        assertThat(ctx.require("pt_id")).isEqualTo("PT-999");
    }

    @Test
    void require_absentKey_throwsWithDiagnostic() {
        ctx.put("existing_key", "value");

        assertThatThrownBy(() -> ctx.require("missing_key"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("missing_key")
            .hasMessageContaining("existing_key");
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    void reset_clearsAllKeys() {
        ctx.put("a", "1");
        ctx.put("b", "2");

        ctx.reset();

        assertThat(ctx.get("a")).isEmpty();
        assertThat(ctx.get("b")).isEmpty();
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Test
    void resolve_substitutesMultipleTokens() {
        ctx.put("inquiry_id", "INQ-001");
        ctx.put("pt_id", "PT-999");

        String result = ctx.resolve("/api/inquiry/${inquiry_id}/pt/${pt_id}");
        assertThat(result).isEqualTo("/api/inquiry/INQ-001/pt/PT-999");
    }

    @Test
    void resolve_leavesUnresolvedTokensIntact() {
        ctx.put("known", "value");

        String result = ctx.resolve("${known} and ${missing}");
        assertThat(result).isEqualTo("value and ${missing}");
    }

    @Test
    void resolve_noTokens_returnsOriginal() {
        String result = ctx.resolve("plain text with no tokens");
        assertThat(result).isEqualTo("plain text with no tokens");
    }

    // ── fresh instance isolation ──────────────────────────────────────────────

    @Test
    void separateInstances_haveIndependentState() {
        ScenarioContext ctx1 = new ScenarioContext();
        ScenarioContext ctx2 = new ScenarioContext();

        ctx1.put("key", "from-ctx1");
        ctx2.put("key", "from-ctx2");

        assertThat(ctx1.require("key")).isEqualTo("from-ctx1");
        assertThat(ctx2.require("key")).isEqualTo("from-ctx2");
    }
}

