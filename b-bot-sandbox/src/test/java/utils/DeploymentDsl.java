package com.bbot.sandbox.utils;

import com.bbot.core.rest.HttpClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DSL for the Deployment Dashboard — both REST API assertions and browser
 * (Playwright) interactions.
 *
 * <p>Construct with {@code null} for the page parameter when only API
 * assertions are needed (e.g. precondition checks in BondBlotter.feature).
 * Construct with a live {@link Page} for grid and filter UI tests.
 */
public final class DeploymentDsl {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient   CLIENT = HttpClientFactory.shared();

    private final Page   page;       // null = API-only mode
    private final String apiBaseUrl;

    public DeploymentDsl(Page page, String apiBaseUrl) {
        this.page       = page;
        this.apiBaseUrl = apiBaseUrl;
    }

    // ── API assertions ────────────────────────────────────────────────────────

    /** Asserts that GET /api/deployments returns an array with at least {@code min} entries. */
    public void assertServiceCount(int min) throws IOException, InterruptedException {
        JsonNode arr = getAll();
        assertThat(arr.isArray()).as("GET /api/deployments should return a JSON array").isTrue();
        assertThat(arr.size())
                .as("Deployment registry should contain at least %d services", min)
                .isGreaterThanOrEqualTo(min);
    }

    /** Asserts the named service is present in the registry. */
    public void assertServicePresent(String name) throws IOException, InterruptedException {
        getService(name); // throws AssertionError if 404
    }

    /**
     * Asserts that the named service has the given status AND version.
     * Used by the BondBlotter precondition scenario to formally prove which
     * software versions were under test when the regression was run.
     */
    public void assertServiceRunningAtVersion(String name, String expectedStatus, String expectedVersion)
            throws IOException, InterruptedException {
        JsonNode rec = getService(name);
        assertThat(rec.get("status").asText())
                .as("Service '%s' status", name).isEqualTo(expectedStatus);
        assertThat(rec.get("version").asText())
                .as("Service '%s' version", name).isEqualTo(expectedVersion);
    }

    /** Asserts that exactly {@code count} services in the registry have the given status. */
    public void assertStatusCount(String status, int count) throws IOException, InterruptedException {
        JsonNode arr = getAll();
        long actual = 0;
        for (JsonNode n : arr) { if (status.equals(n.get("status").asText())) actual++; }
        assertThat(actual).as("Number of %s services", status).isEqualTo(count);
    }

    /** Asserts the named service has the given status. */
    public void assertServiceStatus(String name, String expectedStatus)
            throws IOException, InterruptedException {
        JsonNode rec = getService(name);
        assertThat(rec.get("status").asText())
                .as("Service '%s' status", name).isEqualTo(expectedStatus);
    }

    // ── Browser (UI) interactions ─────────────────────────────────────────────

    /**
     * Navigates to the deployment dashboard and waits for the grid to render.
     * Requires a live Page.
     */
    public void openDashboard() {
        requirePage();
        page.navigate(apiBaseUrl + "/deployment/");
        // Wait for at least one grid row to appear
        page.waitForSelector(".ag-center-cols-container [row-index='0']");
    }

    /** Types the given text into the filter input above the grid. */
    public void filterByName(String text) {
        requirePage();
        page.locator("[aria-label='Filter services']").fill(text);
        // Give the quick filter a moment to propagate through AG Grid's rendering pipeline
        page.waitForTimeout(300);
    }

    /** Clears the filter input. */
    public void clearFilter() {
        requirePage();
        page.locator("[aria-label='Filter services']").fill("");
        page.waitForTimeout(300);
    }

    /** Asserts the grid has at least {@code min} visible rows. */
    public void assertGridHasAtLeastRows(int min) {
        requirePage();
        Locator rows = page.locator(".ag-center-cols-container .ag-row");
        assertThat(rows.count())
                .as("Grid should have at least %d visible rows", min)
                .isGreaterThanOrEqualTo(min);
    }

    /** Asserts a column header with the given col-id is visible. */
    public void assertColumnVisible(String colId) {
        requirePage();
        Locator header = page.locator(".ag-header-cell[col-id='" + colId + "']");
        assertThat(header.count())
                .as("Column '%s' should be visible in the grid", colId)
                .isGreaterThan(0);
    }

    /**
     * Asserts that a cell in the {@code name} column whose text is exactly
     * {@code serviceName} is currently visible (not filtered out).
     */
    public void assertGridContainsService(String serviceName) {
        requirePage();
        Locator cell = page.locator(".ag-center-cols-container .ag-cell[col-id='name']")
                .filter(new Locator.FilterOptions().setHasText(serviceName));
        assertThat(cell.count())
                .as("Service '%s' should be visible in the grid", serviceName)
                .isGreaterThan(0);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private JsonNode getService(String name) throws IOException, InterruptedException {
        String path = "/api/deployments/" + name;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + path))
                .GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode())
                .as("GET %s should return 200 (got %d)", path, resp.statusCode())
                .isEqualTo(200);
        return MAPPER.readTree(resp.body());
    }

    private JsonNode getAll() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/api/deployments"))
                .GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).as("GET /api/deployments should return 200").isEqualTo(200);
        return MAPPER.readTree(resp.body());
    }

    private void requirePage() {
        if (page == null) throw new IllegalStateException("Page is required for UI operations");
    }
}
