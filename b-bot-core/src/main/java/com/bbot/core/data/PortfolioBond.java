package com.bbot.core.data;

/**
 * A single line item in a test portfolio: one bond with its trading parameters.
 *
 * <p>Declared inside a portfolio's {@code bonds} block in HOCON:
 * <pre>{@code
 * bonds {
 *   line-1 {
 *     isin        = "US912828YJ02"
 *     description = "UST 4.25% 2034"
 *     maturity    = "2034-11-15"
 *     coupon      = 4.250
 *     quantity    = 2000000
 *     notional    = 2000000
 *     side        = "Buy"
 *     currency    = "USD"
 *   }
 * }
 * }</pre>
 *
 * <p>All fields except {@code isin}, {@code quantity}, and {@code side} are optional.
 * {@code notional} defaults to {@code quantity} when absent.
 * {@code description}, {@code maturity} default to {@code ""};
 * {@code coupon} defaults to {@code 0.0}.
 *
 * @param isin        ISIN of the bond
 * @param quantity    face-value quantity submitted in the API request
 * @param side        "Buy" or "Sell"
 * @param currency    ISO currency code (e.g. "USD", "EUR", "GBP"); default "USD"
 * @param description human-readable bond descriptor displayed in the blotter
 * @param maturity    ISO-8601 maturity date (e.g. "2034-11-15"); used by the
 *                    blotter's DV01 calculator for spread cross-drive
 * @param coupon      annual coupon rate in percent (e.g. 4.25 for 4.25%); used
 *                    by the blotter's modified-duration approximation
 * @param notional    face value for blotter display; defaults to {@code quantity}
 *                    when not declared in HOCON
 * @param client      counterparty / client name displayed in the blotter Client column;
 *                    default {@code ""}
 * @param bondId      catalogue ID of the bond this line was resolved from
 *                    (e.g. {@code "UST-2Y"}); {@code null} when the line was declared
 *                    inline without a {@code bond = } reference
 */
public record PortfolioBond(
        String isin,
        long   quantity,
        String side,
        String currency,
        String description,
        String maturity,
        double coupon,
        long   notional,
        String client,
        String bondId
) {}
