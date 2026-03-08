package com.bbot.core;

import com.bbot.core.exception.BBotException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link NumericComparator}.
 *
 * <p>Covers: currency symbols, thousand separators, trailing zeros, percentages,
 * negative numbers, non-numeric fallback, JSON field extraction with dot/bracket paths.
 */
class NumericComparatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── equivalent(): numeric comparisons ─────────────────────────────────────

    @Test
    void equivalent_trailingZeros() {
        assertThat(NumericComparator.equivalent("100.00", "100")).isTrue();
    }

    @Test
    void equivalent_thousandSeparators() {
        assertThat(NumericComparator.equivalent("5,937,500.0", "5937500")).isTrue();
    }

    @Test
    void equivalent_currencyPrefix() {
        assertThat(NumericComparator.equivalent("$98.75", "98.75")).isTrue();
    }

    @Test
    void equivalent_euroCurrencyPrefix() {
        assertThat(NumericComparator.equivalent("€1,250.00", "1250")).isTrue();
    }

    @Test
    void equivalent_percentageSuffix() {
        assertThat(NumericComparator.equivalent("4.52%", "4.52")).isTrue();
    }

    @Test
    void equivalent_negativeNumbers() {
        assertThat(NumericComparator.equivalent("-1.5", "-1.50")).isTrue();
    }

    @Test
    void equivalent_numericMismatch() {
        assertThat(NumericComparator.equivalent("98.75", "99.00")).isFalse();
    }

    // ── equivalent(): non-numeric fallback ────────────────────────────────────

    @Test
    void equivalent_nonNumericMatch() {
        assertThat(NumericComparator.equivalent("SUBMITTED", "SUBMITTED")).isTrue();
    }

    @Test
    void equivalent_nonNumericCaseInsensitive() {
        assertThat(NumericComparator.equivalent("Pending", "PENDING")).isTrue();
    }

    @Test
    void equivalent_nonNumericMismatch() {
        assertThat(NumericComparator.equivalent("SUBMITTED", "PENDING")).isFalse();
    }

    // ── equivalent(): edge cases ──────────────────────────────────────────────

    @Test
    void equivalent_bothEmpty() {
        assertThat(NumericComparator.equivalent("", "")).isTrue();
    }

    @Test
    void equivalent_nullInputs() {
        assertThat(NumericComparator.equivalent(null, null)).isTrue();
    }

    @Test
    void equivalent_oneNullOneEmpty() {
        assertThat(NumericComparator.equivalent(null, "")).isTrue();
    }

    @Test
    void equivalent_whitespaceOnly() {
        assertThat(NumericComparator.equivalent("  ", "  ")).isTrue();
    }

    // ── assertEquivalent(): pass / fail ───────────────────────────────────────

    @Test
    void assertEquivalent_passesOnMatch() {
        // Should not throw
        NumericComparator.assertEquivalent("100.00", "100", "price", 0, "price");
    }

    @Test
    void assertEquivalent_throwsOnMismatch() {
        assertThatThrownBy(() ->
                NumericComparator.assertEquivalent("98.75", "99.00", "price", 0, "price"))
            .isInstanceOf(BBotException.class)
            .hasMessageContaining("98.75")
            .hasMessageContaining("99.00")
            .hasMessageContaining("price");
    }

    @Test
    void assertEquivalent_threeArgOverload() {
        // Should not throw
        NumericComparator.assertEquivalent("price", "5937500.0", "5,937,500");
    }

    // ── extractFieldValue(): path navigation ──────────────────────────────────

    @Test
    void extractFieldValue_simplePath() throws Exception {
        JsonNode root = MAPPER.readTree("{\"portfolio_id\":\"PT-001\",\"status\":\"PENDING\"}");
        assertThat(NumericComparator.extractFieldValue(root, "portfolio_id")).isEqualTo("PT-001");
    }

    @Test
    void extractFieldValue_nestedDotPath() throws Exception {
        JsonNode root = MAPPER.readTree("{\"order\":{\"price\":\"98.75\"}}");
        assertThat(NumericComparator.extractFieldValue(root, "order.price")).isEqualTo("98.75");
    }

    @Test
    void extractFieldValue_arrayBracketNotation() throws Exception {
        JsonNode root = MAPPER.readTree("{\"trades\":[{\"price\":\"100.5\"},{\"price\":\"101.0\"}]}");
        assertThat(NumericComparator.extractFieldValue(root, "trades[0].price")).isEqualTo("100.5");
        assertThat(NumericComparator.extractFieldValue(root, "trades[1].price")).isEqualTo("101.0");
    }

    @Test
    void extractFieldValue_missingPath_returnsEmpty() throws Exception {
        JsonNode root = MAPPER.readTree("{\"status\":\"OK\"}");
        assertThat(NumericComparator.extractFieldValue(root, "nonexistent")).isEmpty();
    }

    @Test
    void extractFieldValue_nullNode_returnsEmpty() throws Exception {
        JsonNode root = MAPPER.readTree("{\"field\":null}");
        assertThat(NumericComparator.extractFieldValue(root, "field")).isEmpty();
    }

    // ── normalise(): internal but package-visible ─────────────────────────────

    @Test
    void normalise_stripsAllFormatting() {
        assertThat(NumericComparator.normalise("$5,937,500.00")).isEqualTo("5937500.00");
    }

    @Test
    void normalise_nullReturnsEmpty() {
        assertThat(NumericComparator.normalise(null)).isEmpty();
    }

    @Test
    void normalise_poundAndSpaces() {
        assertThat(NumericComparator.normalise("£ 1,000.50")).isEqualTo("1000.50");
    }
}

