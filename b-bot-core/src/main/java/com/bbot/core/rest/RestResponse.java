package com.bbot.core.rest;

import com.bbot.core.exception.BBotRestException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

/**
 * Immutable snapshot of an HTTP response with fluent JsonPath-based assertions
 * and value-capture support.
 *
 * <p>Returned by {@link RestProbe#get(String)} and {@link RestProbe#post(String, String)}.
 * All assertion methods return {@code this} for chaining.
 *
 * <h2>JsonPath notation</h2>
 * Paths that start with {@code $} are used as-is ({@code $.status}, {@code $[0].isin}).
 * Short paths without {@code $} are normalised automatically: {@code "status"} → {@code "$.status"}.
 *
 * <h2>Example chained usage</h2>
 * <pre>{@code
 * probe.post("/api/inquiry", body)
 *      .assertStatus(201)
 *      .assertField("status", "PENDING")
 *      .assertFieldNotEmpty("inquiry_id")
 *      .capture("inquiry_id");   // → ScenarioState.put("inquiry_id", "INQ-001")
 * }</pre>
 */
public final class RestResponse {

    private final int    status;
    private final String body;
    private final String requestLabel;  // e.g. "POST http://localhost:9099/api/inquiry"

    RestResponse(int status, String body, String requestLabel) {
        this.status       = status;
        this.body         = body == null ? "" : body;
        this.requestLabel = requestLabel;
    }

    // ── Status assertion ──────────────────────────────────────────────────────

    /**
     * Asserts that the HTTP status code equals {@code expected}.
     *
     * @throws AssertionError with the response body on mismatch
     */
    public RestResponse assertStatus(int expected) {
        if (status != expected)
            throw new BBotRestException(String.format(
                "%s — expected HTTP %d but got %d%nBody: %s",
                requestLabel, expected, status, body),
                extractMethod(), extractUrl(), status, body);
        return this;
    }

    // ── Field assertions ──────────────────────────────────────────────────────

    /**
     * Asserts that the JSON field at {@code jsonPath} equals {@code expected}.
     *
     * @param jsonPath path to the field — short form ({@code "status"}) or full
     *                 JsonPath ({@code "$.items[0].isin"})
     * @throws AssertionError with actual value and full body on mismatch
     */
    public RestResponse assertField(String jsonPath, String expected) {
        String actual = getField(jsonPath);
        if (!expected.equals(actual))
            throw new BBotRestException(String.format(
                "Field '%s': expected \"%s\" but was \"%s\"%nBody: %s",
                jsonPath, expected, actual, body),
                extractMethod(), extractUrl(), status, body);
        return this;
    }

    /**
     * Asserts that the JSON field at {@code jsonPath} is present, non-null, and non-blank.
     *
     * @throws AssertionError if the field is absent, null, or blank
     */
    public RestResponse assertFieldNotEmpty(String jsonPath) {
        String actual = getField(jsonPath);
        if (actual == null || actual.isBlank())
            throw new BBotRestException(String.format(
                "Field '%s' expected to be non-empty but was: \"%s\"%nBody: %s",
                jsonPath, actual, body),
                extractMethod(), extractUrl(), status, body);
        return this;
    }

    // ── Value extraction ──────────────────────────────────────────────────────

    /**
     * Extracts the JSON field at {@code jsonPath} as a String.
     *
     * @throws AssertionError if the path does not exist or the body is not valid JSON
     */
    public String getField(String jsonPath) {
        if (body.isBlank())
            throw new BBotRestException(
                "Cannot read field '" + jsonPath + "' — response body is empty. " + requestLabel,
                extractMethod(), extractUrl(), status, body);
        String path = jsonPath.startsWith("$") ? jsonPath : "$." + jsonPath;
        try {
            Object value = JsonPath.read(body, path);
            return value == null ? null : value.toString();
        } catch (PathNotFoundException e) {
            throw new BBotRestException(String.format(
                "JSON path '%s' not found in response.%nBody: %s", path, body),
                extractMethod(), extractUrl(), status, body);
        } catch (Exception e) {
            throw new BBotRestException(String.format(
                "Failed to read JSON path '%s' from response.%nBody: %s", path, body),
                extractMethod(), extractUrl(), status, body);
        }
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    /**
     * Reads the value at {@code jsonPath} and stores it in {@link ScenarioState}
     * under {@code alias}, making it available as {@code ${alias}} in subsequent
     * step paths and templates.
     *
     * @return {@code this} for chaining
     */
    public RestResponse capture(String jsonPath, String alias) {
        ScenarioState.current().put(alias, getField(jsonPath));
        return this;
    }

    /**
     * Reads the value at {@code jsonPath} and stores it in {@link ScenarioState}.
     * The alias is the last segment of the path, e.g. {@code "$.inquiry_id"} → {@code "inquiry_id"}.
     *
     * @return {@code this} for chaining
     */
    public RestResponse capture(String jsonPath) {
        String path = jsonPath.startsWith("$") ? jsonPath : "$." + jsonPath;
        String alias = path.substring(path.lastIndexOf('.') + 1)
                          .replaceAll("[^a-zA-Z0-9_-]", "");
        return capture(jsonPath, alias);
    }

    // ── Raw accessors ─────────────────────────────────────────────────────────

    /** Returns the HTTP status code. */
    public int    status() { return status; }

    /** Returns the raw response body as a String. */
    public String body()   { return body;   }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Extracts the HTTP method from the request label (e.g. "POST" from "POST http://..."). */
    private String extractMethod() {
        int space = requestLabel.indexOf(' ');
        return space > 0 ? requestLabel.substring(0, space) : "";
    }

    /** Extracts the URL from the request label (e.g. "http://..." from "POST http://..."). */
    private String extractUrl() {
        int space = requestLabel.indexOf(' ');
        return space > 0 ? requestLabel.substring(space + 1) : requestLabel;
    }
}
