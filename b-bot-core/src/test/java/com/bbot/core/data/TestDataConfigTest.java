package com.bbot.core.data;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.exception.BBotConfigException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TestDataConfig}.
 *
 * <p>Uses test HOCON config from {@code b-bot-core/src/test/resources/application.conf}
 * which declares bond-lists, templates, portfolios, users, and service-versions.
 */
class TestDataConfigTest {

    private static TestDataConfig testData;

    @BeforeAll
    static void loadConfig() {
        testData = BBotConfig.load().getTestData();
    }

    // ── Global variables ──────────────────────────────────────────────────────

    @Test
    void getGlobal_present() {
        assertThat(testData.getGlobal("settlement-date")).hasValue("2026-03-21");
    }

    @Test
    void getGlobal_absent() {
        assertThat(testData.getGlobal("nonexistent-key")).isEmpty();
    }

    @Test
    void getAllGlobals_excludesReservedBlocks() {
        Map<String, String> globals = testData.getAllGlobals();

        // Should contain top-level scalars
        assertThat(globals).containsKey("settlement-date");
        assertThat(globals).containsKey("custom-global");

        // Should NOT contain reserved sub-blocks
        assertThat(globals.keySet().stream()
                .noneMatch(k -> k.startsWith("bond-lists")
                        || k.startsWith("templates")
                        || k.startsWith("portfolios")
                        || k.startsWith("service-versions")
                        || k.startsWith("users")))
            .as("Globals should exclude bond-lists, templates, portfolios, service-versions, users")
            .isTrue();
    }

    // ── Bond lists ────────────────────────────────────────────────────────────

    @Test
    void getBondList_present() {
        Map<String, String> bonds = testData.getBondList("TEST_BONDS");
        assertThat(bonds)
            .containsEntry("ISIN1", "US912828YJ02")
            .containsEntry("ISIN2", "XS2346573523");
    }

    @Test
    void getBondList_isUnmodifiable() {
        Map<String, String> bonds = testData.getBondList("TEST_BONDS");
        assertThatThrownBy(() -> bonds.put("ISIN3", "NEW"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getBondList_absent_throws() {
        assertThatThrownBy(() -> testData.getBondList("NONEXISTENT"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("NONEXISTENT")
            .hasMessageContaining("not found");
    }

    @Test
    void resolveBondRef_present() {
        assertThat(testData.resolveBondRef("TEST_BONDS", "ISIN1"))
            .isEqualTo("US912828YJ02");
    }

    @Test
    void resolveBondRef_missingField_throws() {
        assertThatThrownBy(() -> testData.resolveBondRef("TEST_BONDS", "ISIN99"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("ISIN99")
            .hasMessageContaining("ISIN1");  // should list available fields
    }

    // ── Template registry ─────────────────────────────────────────────────────

    @Test
    void getTemplatePath_present() {
        assertThat(testData.getTemplatePath("test-rfq"))
            .isEqualTo("templates/test-rfq.json");
    }

    @Test
    void getTemplatePath_absent_throws() {
        assertThatThrownBy(() -> testData.getTemplatePath("nonexistent"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("nonexistent")
            .hasMessageContaining("not registered");
    }

    // ── Service versions ──────────────────────────────────────────────────────

    @Test
    void getServiceVersion_present() {
        assertThat(testData.getServiceVersion("credit-rfq-blotter"))
            .isEqualTo("v2.4.1");
    }

    @Test
    void getServiceVersion_absent_throws() {
        assertThatThrownBy(() -> testData.getServiceVersion("nonexistent-svc"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("nonexistent-svc");
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @Test
    void getUser_present() {
        assertThat(testData.getUser("trader")).isEqualTo("doej");
        assertThat(testData.getUser("admin")).isEqualTo("smithj");
    }

    @Test
    void getUser_absent_throws() {
        assertThatThrownBy(() -> testData.getUser("nonexistent-role"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("nonexistent-role");
    }

    // ── Portfolios ────────────────────────────────────────────────────────────

    @Test
    void getPortfolio_parsesBondsInOrder() {
        Portfolio portfolio = testData.getPortfolio("TEST_PT");

        assertThat(portfolio.ptId()).isEqualTo("PT_TEST_001");
        assertThat(portfolio.settlementDate()).isEqualTo("2026-06-15");
        assertThat(portfolio.bonds()).hasSize(2);

        // Verify ordering (line-1 before line-2)
        var keys = portfolio.bonds().keySet().stream().toList();
        assertThat(keys).containsExactly("line-1", "line-2");

        PortfolioBond bond1 = portfolio.bonds().get("line-1");
        assertThat(bond1.isin()).isEqualTo("US912828YJ02");
        assertThat(bond1.quantity()).isEqualTo(2_000_000);
        assertThat(bond1.side()).isEqualTo("Buy");
        assertThat(bond1.currency()).isEqualTo("USD");

        PortfolioBond bond2 = portfolio.bonds().get("line-2");
        assertThat(bond2.isin()).isEqualTo("XS2346573523");
        assertThat(bond2.side()).isEqualTo("Sell");
    }

    @Test
    void getPortfolio_fallbackSettlementDate() {
        // NO_DATE_PT omits settlement-date — should fall back to global
        Portfolio portfolio = testData.getPortfolio("NO_DATE_PT");

        assertThat(portfolio.settlementDate()).isEqualTo("2026-03-21");
    }

    @Test
    void getPortfolio_absent_throws() {
        assertThatThrownBy(() -> testData.getPortfolio("NONEXISTENT"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("NONEXISTENT");
    }

    @Test
    void getPortfolio_defaultCurrency() {
        // NO_DATE_PT → line-1 has currency = "GBP" (explicit)
        Portfolio portfolio = testData.getPortfolio("NO_DATE_PT");
        assertThat(portfolio.bonds().get("line-1").currency()).isEqualTo("GBP");
    }
}

