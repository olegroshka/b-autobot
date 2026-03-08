package com.bbot.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the pure (non-Playwright) functions in {@link TickingCellHelper}.
 *
 * <p>The Playwright-dependent methods (waitForCellUpdate, etc.) require a live browser
 * and are covered by the Cucumber integration tests. This class tests the static
 * utility functions that can be exercised without a browser.
 */
class TickingCellHelperTest {

    // ── buildCellSelector ─────────────────────────────────────────────────────

    @Test
    void buildCellSelector_format() {
        String selector = TickingCellHelper.buildCellSelector("twPrice", 3);
        assertThat(selector).isEqualTo(
                ".ag-center-cols-container [row-index='3'] [col-id='twPrice']");
    }

    @Test
    void buildCellSelector_zeroIndex() {
        String selector = TickingCellHelper.buildCellSelector("price", 0);
        assertThat(selector).isEqualTo(
                ".ag-center-cols-container [row-index='0'] [col-id='price']");
    }

    @Test
    void buildCellSelector_differentColumns() {
        assertThat(TickingCellHelper.buildCellSelector("status", 5))
                .contains("[col-id='status']")
                .contains("[row-index='5']");
    }

    // ── parseNumeric ──────────────────────────────────────────────────────────

    @Test
    void parseNumeric_plainNumber() {
        assertThat(TickingCellHelper.parseNumeric("98.75")).isEqualTo(98.75);
    }

    @Test
    void parseNumeric_commasAndCurrency() {
        assertThat(TickingCellHelper.parseNumeric("$5,937,500.00")).isEqualTo(5_937_500.00);
    }

    @Test
    void parseNumeric_integerValue() {
        assertThat(TickingCellHelper.parseNumeric("100")).isEqualTo(100.0);
    }

    @Test
    void parseNumeric_whitespace() {
        assertThat(TickingCellHelper.parseNumeric(" 42.5 ")).isEqualTo(42.5);
    }

    @Test
    void parseNumeric_blankText_throws() {
        assertThatThrownBy(() -> TickingCellHelper.parseNumeric(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no parseable numeric content");
    }

    @Test
    void parseNumeric_nonNumericText_throws() {
        assertThatThrownBy(() -> TickingCellHelper.parseNumeric("PENDING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no parseable numeric content");
    }
}

