package com.bbot.core.rest;

import com.bbot.core.config.BBotConfig;
import com.bbot.core.data.Bond;
import com.bbot.core.exception.BBotConfigException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JsonTemplateEngine}.
 *
 * <p>Uses test HOCON config from {@code b-bot-core/src/test/resources/application.conf}
 * and template files from {@code b-bot-core/src/test/resources/templates/}.
 */
class JsonTemplateEngineTest {

    private JsonTemplateEngine engine;
    private ScenarioContext ctx;

    @BeforeEach
    void setUp() {
        BBotConfig cfg = BBotConfig.load();
        ctx = new ScenarioContext();
        engine = new JsonTemplateEngine(cfg.getTestData(), ctx);
    }

    @AfterEach
    void cleanup() {
        ctx.reset();
    }

    // ── Bond list token substitution ──────────────────────────────────────────

    @Test
    void render_bondListTokens() {
        String result = engine.render("test-rfq", "TEST_BONDS");

        assertThat(result).contains("\"US912828YJ02\"");  // ${bond.ISIN1} resolved
        assertThat(result).doesNotContain("${bond.ISIN1}");
    }

    // ── Global token substitution ─────────────────────────────────────────────

    @Test
    void render_globalTokens() {
        String result = engine.render("test-rfq", "TEST_BONDS");

        assertThat(result).contains("2026-03-21");  // ${settlement-date} resolved
        assertThat(result).doesNotContain("${settlement-date}");
    }

    // ── Scenario state token substitution ─────────────────────────────────────

    @Test
    void render_scenarioStateTokens() {
        ctx.put("inquiry_id", "INQ-TEST-999");

        String result = engine.render("test-quote");

        assertThat(result).contains("INQ-TEST-999");
        assertThat(result).doesNotContain("${inquiry_id}");
    }

    // ── Unresolved tokens left in place ───────────────────────────────────────

    @Test
    void render_unresolvedTokensLeftInPlace() {
        // test-quote.json has ${inquiry_id} — don't put it in state
        String result = engine.render("test-quote");

        assertThat(result).contains("${inquiry_id}");
    }

    // ── No bond list overload ─────────────────────────────────────────────────

    @Test
    void render_noBondList() {
        ctx.put("inquiry_id", "INQ-456");

        String result = engine.render("test-quote");

        assertThat(result).contains("INQ-456");
    }

    // ── renderWithContext ─────────────────────────────────────────────────────

    @Test
    void renderWithContext_customVars() {
        String result = engine.renderWithContext("test-quote",
                java.util.Map.of("inquiry_id", "INQ-CUSTOM"));

        assertThat(result).contains("INQ-CUSTOM");
        assertThat(result).doesNotContain("${inquiry_id}");
    }

    // ── renderWithBond ────────────────────────────────────────────────────────

    @Test
    void renderWithBond_substitutesAllCatalogueFields() {
        Bond bond = new Bond("UST-2Y", "US912828YJ02", "UST 4.25% 2034", "2034-11-15", 4.250);

        String result = engine.renderWithBond("test-rfq-catalogue", bond);

        assertThat(result).contains("\"US912828YJ02\"");       // bond.isin
        assertThat(result).contains("\"UST 4.25% 2034\"");     // bond.description
        assertThat(result).contains("\"2034-11-15\"");         // bond.maturity
        assertThat(result).contains("4.25");                   // bond.coupon
        assertThat(result).contains("2026-03-21");             // ${settlement-date} global
        assertThat(result).doesNotContain("${bond.");
    }

    // ── Missing template ──────────────────────────────────────────────────────

    @Test
    void render_missingTemplate_throws() {
        assertThatThrownBy(() -> engine.render("nonexistent-template"))
            .isInstanceOf(BBotConfigException.class)
            .hasMessageContaining("nonexistent-template")
            .hasMessageContaining("not registered");
    }

    // ── Resolution order: state > bond > global ──────────────────────────────

    @Test
    void render_resolutionOrder_stateWins() {
        ctx.put("settlement-date", "2099-12-31");

        String result = engine.render("test-rfq", "TEST_BONDS");

        // State wins — should see 2099, not the global 2026
        assertThat(result).contains("2099-12-31");
    }
}

