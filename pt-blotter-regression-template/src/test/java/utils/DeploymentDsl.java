package utils;

import com.bbot.core.rest.HttpClientFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * REST DSL for the Deployment Dashboard service registry.
 *
 * <p>Uses the shared JDK {@link HttpClient} from {@link HttpClientFactory} —
 * no Playwright, no browser.
 * All methods throw {@link AssertionError} on unexpected responses.
 *
 * <p>The API base URL is injected from the active environment config
 * ({@code b-bot.apps.deployment.apiBase}) — no hardcoded hosts.
 *
 * <h2>API shape</h2>
 * <pre>
 *   GET /api/deployments        → JSON array of service records
 *   GET /api/deployments/{name} → JSON object for one service
 * </pre>
 *
 * <p>Each record contains: {@code name}, {@code status}, {@code version},
 * {@code environment}, {@code team}, {@code lastDeployed}, {@code build}, {@code uptime}.
 */
public final class DeploymentDsl {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};
    private static final HttpClient CLIENT = HttpClientFactory.shared();

    private final String apiBase;

    public DeploymentDsl(String apiBase) {
        this.apiBase = apiBase;
    }

    // ── Registry queries ──────────────────────────────────────────────────────

    /**
     * Asserts that a service with the given name appears in the deployment registry.
     */
    public void assertServiceInRegistry(String serviceName) {
        findService(serviceName); // throws if not found
    }

    /**
     * Asserts that a service has the expected status AND version.
     *
     * <p>Both must match; if either differs the assertion fails with a clear diff message.
     */
    public void assertServiceStatusAndVersion(String name, String expectedStatus, String expectedVersion) {
        Map<String, Object> svc = findService(name);
        String actualStatus = String.valueOf(svc.get("status"));
        String actualVersion = String.valueOf(svc.get("version"));

        StringBuilder problems = new StringBuilder();
        if (!expectedStatus.equals(actualStatus))
            problems.append("  status: expected '").append(expectedStatus)
                    .append("' but was '").append(actualStatus).append("'\n");
        if (!expectedVersion.equals(actualVersion))
            problems.append("  version: expected '").append(expectedVersion)
                    .append("' but was '").append(actualVersion).append("'\n");

        if (!problems.isEmpty())
            throw new AssertionError(
                    "Service '" + name + "' did not match expected state:\n" + problems);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Map<String, Object> findService(String name) {
        List<Map<String, Object>> services = fetchAll();
        return services.stream()
                .filter(s -> name.equals(s.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Service '" + name + "' not found in deployment registry. " +
                                "Registered services: " +
                                services.stream().map(s -> String.valueOf(s.get("name"))).toList()));
    }

    private List<Map<String, Object>> fetchAll() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/api/deployments"))
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400)
                throw new AssertionError(
                        "GET /api/deployments returned HTTP " + resp.statusCode() +
                                ". Is the Deployment service running at " + apiBase + "?");
            return MAPPER.readValue(resp.body(), LIST_TYPE);
        } catch (Exception e) {
            throw new AssertionError(
                    "Failed to fetch deployment registry from " + apiBase +
                            "\nIs the mock UAT environment running? " +
                            "Start it with: scripts/start-mock-uat.sh  (or .bat on Windows)", e);
        }
    }
}
