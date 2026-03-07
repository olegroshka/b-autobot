package com.bbot.core.data;

import java.util.LinkedHashMap;

/**
 * A named test portfolio: a collection of bonds submitted together as one PT (pricing ticket).
 *
 * <p>Portfolios are declared in {@code b-bot.test-data.portfolios} and represent
 * a complete pricing-ticket structure with its own PT ID — deliberately different
 * from the seed PT IDs so dynamic submissions are distinguishable in the blotter grid.
 *
 * <h2>HOCON declaration</h2>
 * <pre>{@code
 * b-bot.test-data.portfolios {
 *   HYPT_1 {
 *     pt-id           = "PT_TEST_20260321_AA01"   // distinct from seed defaults
 *     settlement-date = "2026-03-21"              // optional; falls back to global
 *     bonds {
 *       line-1 { isin = "US912828YJ02", quantity = 2000000, side = "Buy",  currency = "USD" }
 *       line-2 { isin = "XS2346573523", quantity = 1500000, side = "Sell", currency = "EUR" }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h2>Step usage</h2>
 * <pre>{@code
 * Given I submit all inquiries for portfolio "HYPT_1"
 * And the PT-Blotter is open
 * Then the row with ISIN "US912828YJ02" should have status "PENDING"
 * }</pre>
 *
 * @param name            the portfolio's short name (HOCON key)
 * @param ptId            PT ID to use for submitted inquiries (e.g. "PT_TEST_20260321_AA01")
 * @param settlementDate  settlement date string (ISO-8601, e.g. "2026-03-21")
 * @param bonds           ordered map of line-key → {@link PortfolioBond}
 *                        (ordered by HOCON key: line-1, line-2, …)
 */
public record Portfolio(
        String name,
        String ptId,
        String settlementDate,
        LinkedHashMap<String, PortfolioBond> bonds
) {}
