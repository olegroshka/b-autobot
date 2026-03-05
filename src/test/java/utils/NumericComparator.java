package utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Numeric-precision-aware comparator for UI cell values vs API JSON values.
 *
 * <h2>Problem</h2>
 * The same number can appear in different textual forms across the API response
 * and the AG Grid UI:
 * <ul>
 *   <li>{@code 100} vs {@code 100.00} — trailing decimal zeros</li>
 *   <li>{@code 5,937,500.0} vs {@code 5937500} — thousand separators</li>
 *   <li>{@code $98.75} vs {@code 98.75} — currency prefix</li>
 *   <li>{@code 4.52%} vs {@code 4.52} — percentage suffix</li>
 * </ul>
 *
 * <h2>Solution</h2>
 * Strip formatting characters, then compare as {@link BigDecimal} using
 * {@link BigDecimal#compareTo} (which ignores scale differences, so
 * {@code 100.00.compareTo(100) == 0}).  Falls back to case-insensitive string
 * equality for non-numeric values.
 *
 * <h2>Field path navigation</h2>
 * {@link #extractFieldValue(JsonNode, String)} supports both simple field names
 * ({@code "portfolio_id"}) and dot/bracket paths ({@code "trades[0].price"})
 * for navigating into nested JSON structures.
 */
public final class NumericComparator {

    /** Characters that are formatting noise in UI-rendered numbers. */
    private static final String STRIP_PATTERN = "[,$£€¥%\\s]";

    private NumericComparator() {}

    // ── Core comparison ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code uiText} and {@code apiText} represent the
     * same value after normalisation.
     *
     * <p>Numeric strings are compared as {@link BigDecimal} (scale-insensitive):
     * <pre>{@code
     *   equivalent("5,937,500.0", "5937500")   // true
     *   equivalent("100.00",      "100")        // true
     *   equivalent("$98.75",      "98.75")      // true
     *   equivalent("SUBMITTED",   "SUBMITTED")  // true (string fallback)
     *   equivalent("SUBMITTED",   "PENDING")    // false
     * }</pre>
     *
     * @param uiText  Text scraped from the AG Grid cell
     * @param apiText Text extracted from the API JSON field
     * @return {@code true} when values are semantically equal
     */
    public static boolean equivalent(String uiText, String apiText) {
        String a = normalise(uiText);
        String b = normalise(apiText);

        if (a.isEmpty() && b.isEmpty()) return true;

        try {
            // BigDecimal.compareTo ignores trailing zeros (100.00 == 100)
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        } catch (NumberFormatException e) {
            // Not numeric — fall back to case-insensitive string comparison
            return a.equalsIgnoreCase(b);
        }
    }

    /**
     * Asserts that {@code uiText} and {@code apiText} are equivalent, throwing
     * an {@link AssertionError} with a descriptive message if they are not.
     *
     * @param uiText     The raw cell text scraped from the grid
     * @param apiText    The expected value extracted from the API JSON
     * @param colId      Column identifier (for the error message)
     * @param rowIndex   Row index (for the error message)
     * @param apiField   JSON field path (for the error message)
     */
    public static void assertEquivalent(
            String uiText, String apiText, String colId, int rowIndex, String apiField) {
        if (!equivalent(uiText, apiText)) {
            throw new AssertionError(String.format(
                    "Numeric/value mismatch for [col-id='%s'][row=%d]:%n" +
                    "  UI value  : '%s' (normalised: '%s')%n" +
                    "  API field : '%s' → '%s' (normalised: '%s')%n" +
                    "  Tip: check AG Grid valueFormatter or number precision in the API stub.",
                    colId, rowIndex,
                    uiText,  normalise(uiText),
                    apiField, apiText, normalise(apiText)));
        }
    }

    // ── JSON field extraction ─────────────────────────────────────────────────

    /**
     * Extracts a value from a Jackson {@link JsonNode} using a dot/bracket path.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "portfolio_id"}  → {@code root.get("portfolio_id").asText()}</li>
     *   <li>{@code "trades[0].price"} → {@code root.get("trades").get(0).get("price").asText()}</li>
     * </ul>
     *
     * @param root Root node of the JSON document
     * @param path Dot/bracket-notation path to the target field
     * @return The field value as a String, or {@code ""} if the path is absent
     */
    public static String extractFieldValue(JsonNode root, String path) {
        // Normalise bracket notation to dot notation: "trades[0].price" → "trades.0.price"
        String dotPath = path.replace("[", ".").replace("]", "");
        String[] segments = dotPath.split("\\.");

        JsonNode cursor = root;
        for (String segment : segments) {
            if (cursor == null || cursor.isMissingNode() || cursor.isNull()) {
                return "";
            }
            // Try as array index first
            try {
                cursor = cursor.get(Integer.parseInt(segment));
            } catch (NumberFormatException e) {
                cursor = cursor.get(segment);
            }
        }

        return (cursor == null || cursor.isMissingNode() || cursor.isNull())
                ? ""
                : cursor.asText();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Strips formatting noise from a string to leave only a parseable number
     * or plain text value.
     */
    static String normalise(String raw) {
        if (raw == null) return "";
        return raw.replaceAll(STRIP_PATTERN, "").trim();
    }
}
