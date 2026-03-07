package com.bbot.core.data;

/**
 * A single line item in a test portfolio: one bond with its trading parameters.
 *
 * <p>Declared inside a portfolio's {@code bonds} block in HOCON:
 * <pre>{@code
 * bonds {
 *   line-1 { isin = "US912828YJ02", quantity = 2000000, side = "Buy",  currency = "USD" }
 *   line-2 { isin = "XS2346573523", quantity = 1500000, side = "Sell", currency = "EUR" }
 * }
 * }</pre>
 *
 * @param isin      ISIN of the bond
 * @param quantity  face-value quantity in base currency units
 * @param side      "Buy" or "Sell"
 * @param currency  ISO currency code (e.g. "USD", "EUR", "GBP")
 */
public record PortfolioBond(
        String isin,
        long   quantity,
        String side,
        String currency
) {}
