package com.bbot.core.rest;

import com.bbot.core.exception.BBotRestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RestResponse}.
 *
 * <p>Covers: status assertion, field assertion (short-form and full JsonPath),
 * field-not-empty, capture with auto/explicit alias, empty body handling,
 * path-not-found, chaining.
 */
class RestResponseTest {

    @AfterEach
    void cleanup() {
        ScenarioState.reset();
    }

    // ── assertStatus ──────────────────────────────────────────────────────────

    @Test
    void assertStatus_passes() {
        RestResponse resp = new RestResponse(201, "{\"id\":\"INQ-001\"}", "POST /api/inquiry");
        resp.assertStatus(201); // should not throw
    }

    @Test
    void assertStatus_failsWithBody() {
        RestResponse resp = new RestResponse(500, "{\"error\":\"internal\"}", "POST /api/inquiry");

        assertThatThrownBy(() -> resp.assertStatus(201))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("500")
            .hasMessageContaining("201")
            .hasMessageContaining("internal");
    }

    // ── assertField ───────────────────────────────────────────────────────────

    @Test
    void assertField_shortForm() {
        RestResponse resp = new RestResponse(200, "{\"status\":\"PENDING\"}", "GET /api");
        resp.assertField("status", "PENDING"); // should not throw
    }

    @Test
    void assertField_fullJsonPath() {
        RestResponse resp = new RestResponse(200,
                "[{\"isin\":\"US912828YJ02\"},{\"isin\":\"XS2346573523\"}]",
                "GET /api/inquiries");
        resp.assertField("$[0].isin", "US912828YJ02");
        resp.assertField("$[1].isin", "XS2346573523");
    }

    @Test
    void assertField_mismatch_showsActualAndBody() {
        RestResponse resp = new RestResponse(200, "{\"status\":\"QUOTED\"}", "GET /api");

        assertThatThrownBy(() -> resp.assertField("status", "PENDING"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("PENDING")
            .hasMessageContaining("QUOTED")
            .hasMessageContaining("status");
    }

    // ── assertFieldNotEmpty ───────────────────────────────────────────────────

    @Test
    void assertFieldNotEmpty_passes() {
        RestResponse resp = new RestResponse(200, "{\"inquiry_id\":\"INQ-001\"}", "POST /api");
        resp.assertFieldNotEmpty("inquiry_id"); // should not throw
    }

    @Test
    void assertFieldNotEmpty_blankField_throws() {
        RestResponse resp = new RestResponse(200, "{\"inquiry_id\":\"\"}", "POST /api");

        assertThatThrownBy(() -> resp.assertFieldNotEmpty("inquiry_id"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("inquiry_id")
            .hasMessageContaining("non-empty");
    }

    @Test
    void assertFieldNotEmpty_nullField_throws() {
        RestResponse resp = new RestResponse(200, "{\"inquiry_id\":null}", "POST /api");

        assertThatThrownBy(() -> resp.assertFieldNotEmpty("inquiry_id"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("inquiry_id");
    }

    // ── getField ──────────────────────────────────────────────────────────────

    @Test
    void getField_emptyBody_throws() {
        RestResponse resp = new RestResponse(200, "", "GET /api");

        assertThatThrownBy(() -> resp.getField("status"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void getField_pathNotFound_throws() {
        RestResponse resp = new RestResponse(200, "{\"status\":\"OK\"}", "GET /api");

        assertThatThrownBy(() -> resp.getField("nonexistent"))
            .isInstanceOf(BBotRestException.class)
            .hasMessageContaining("nonexistent")
            .hasMessageContaining("not found");
    }

    @Test
    void getField_nestedPath() {
        RestResponse resp = new RestResponse(200,
                "{\"data\":{\"inquiry_id\":\"INQ-999\"}}",
                "GET /api");
        assertThat(resp.getField("$.data.inquiry_id")).isEqualTo("INQ-999");
    }

    // ── capture ───────────────────────────────────────────────────────────────

    @Test
    void capture_autoAlias_fromShortForm() {
        RestResponse resp = new RestResponse(201,
                "{\"inquiry_id\":\"INQ-001\"}",
                "POST /api/inquiry");

        resp.capture("inquiry_id");

        assertThat(ScenarioState.require("inquiry_id")).isEqualTo("INQ-001");
    }

    @Test
    void capture_autoAlias_fromFullPath() {
        RestResponse resp = new RestResponse(201,
                "{\"inquiry_id\":\"INQ-002\"}",
                "POST /api/inquiry");

        resp.capture("$.inquiry_id");

        assertThat(ScenarioState.require("inquiry_id")).isEqualTo("INQ-002");
    }

    @Test
    void capture_explicitAlias() {
        RestResponse resp = new RestResponse(201,
                "{\"inquiry_id\":\"INQ-003\"}",
                "POST /api/inquiry");

        resp.capture("inquiry_id", "rfq-id");

        assertThat(ScenarioState.require("rfq-id")).isEqualTo("INQ-003");
    }

    // ── chaining ──────────────────────────────────────────────────────────────

    @Test
    void chainingReturnsThis() {
        RestResponse resp = new RestResponse(201,
                "{\"status\":\"PENDING\",\"inquiry_id\":\"INQ-001\"}",
                "POST /api/inquiry");

        RestResponse result = resp
                .assertStatus(201)
                .assertField("status", "PENDING")
                .assertFieldNotEmpty("inquiry_id")
                .capture("inquiry_id");

        assertThat(result).isSameAs(resp);
        assertThat(ScenarioState.require("inquiry_id")).isEqualTo("INQ-001");
    }

    // ── raw accessors ─────────────────────────────────────────────────────────

    @Test
    void rawAccessors() {
        RestResponse resp = new RestResponse(404, "not found", "GET /missing");
        assertThat(resp.status()).isEqualTo(404);
        assertThat(resp.body()).isEqualTo("not found");
    }
}

