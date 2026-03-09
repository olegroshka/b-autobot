package com.bbot.core.data;

/**
 * Instrument-level bond reference data from the {@code b-bot.test-data.bonds} catalogue.
 *
 * <p>Each bond is defined once in the catalogue and referenced by its {@code id} from
 * portfolio lines and step definitions. This eliminates the duplication that arose
 * when ISINs and reference data were copied into every portfolio block.
 *
 * <p>HOCON structure:
 * <pre>{@code
 * b-bot.test-data.bonds {
 *   UST-2Y { isin = "US912828YJ02", description = "UST 4.25% 2034",
 *             maturity = "2034-11-15", coupon = 4.250 }
 * }
 * }</pre>
 *
 * @param id          catalogue key used to reference this bond (e.g. {@code "UST-2Y"})
 * @param isin        ISIN identifier (e.g. {@code "US912828YJ02"})
 * @param description human-readable bond name (e.g. {@code "UST 4.25% 2034"})
 * @param maturity    ISO-8601 maturity date string (e.g. {@code "2034-11-15"})
 * @param coupon      annual coupon rate in percent (e.g. {@code 4.25} for 4.25%)
 */
public record Bond(String id, String isin, String description, String maturity, double coupon) {}
