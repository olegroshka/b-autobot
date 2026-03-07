package stepdefs;

import com.bbot.core.data.TestDataConfig;
import com.bbot.core.registry.BBotRegistry;
import com.bbot.core.rest.JsonTemplateEngine;
import com.bbot.core.rest.RestProbe;
import com.bbot.core.rest.RestResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Generic REST API step definitions powered by {@link RestProbe}, {@link JsonTemplateEngine},
 * and the config-driven {@link TestDataConfig}.
 *
 * <p>These steps are app-agnostic — they work against any registered application
 * by referencing its name as declared in {@code b-bot.apps.{name}.apiBase}.
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li>No hardcoded URLs, ISINs, or dates — everything comes from config or bond lists.</li>
 *   <li>JSON templates live in {@code src/test/resources/templates/} and are referenced
 *       by short name in the feature file.</li>
 *   <li>Values captured from one response (e.g. {@code inquiry_id}) are stored in
 *       {@link com.bbot.core.rest.ScenarioState} and automatically resolved as
 *       {@code ${inquiry_id}} in subsequent paths and templates.</li>
 *   <li>Bond list references ({@code bond "HYPT_1" field "ISIN1"}) are resolved against
 *       {@code b-bot.test-data.bond-lists.HYPT_1.ISIN1} in your env config.</li>
 * </ul>
 *
 * <h2>Sample scenarios</h2>
 * <pre>{@code
 * When I POST template "credit-rfq" with bond list "HYPT_1" to app "blotter" path "/api/inquiry"
 * Then the response status should be 201
 * And  the response field "status" should be "PENDING"
 * And  the response field "isin" should equal bond "HYPT_1" field "ISIN1"
 * And  I capture the response field "inquiry_id"
 *
 * When I POST template "quote-inquiry" to app "blotter" path "/api/inquiry/${inquiry_id}/quote"
 * Then the response status should be 200
 * And  the response field "status" should be "QUOTED"
 * }</pre>
 */
public class RestApiSteps {

    /**
     * The most recent REST response — shared across steps within the same scenario
     * (Cucumber creates one {@code RestApiSteps} instance per scenario).
     */
    private RestResponse lastResponse;

    private final TestDataConfig    testData;
    private final JsonTemplateEngine templateEngine;

    public RestApiSteps() {
        TestDataConfig td = BBotRegistry.getConfig().getTestData();
        this.testData       = td;
        this.templateEngine = new JsonTemplateEngine(td);
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    /**
     * Renders the named template substituting {@code ${bond.FIELD}} tokens from the
     * named bond list, global test-data scalars, and any previously captured state.
     * Then POSTs the result to {@code {appApiBase}{path}}.
     *
     * <p>Example:
     * <pre>{@code
     * When I POST template "credit-rfq" with bond list "HYPT_1" to app "blotter" path "/api/inquiry"
     * }</pre>
     */
    @When("I POST template {string} with bond list {string} to app {string} path {string}")
    public void postTemplateWithBondList(String template, String bondList,
                                         String app, String path) {
        String body    = templateEngine.render(template, bondList);
        String apiBase = BBotRegistry.getConfig().getAppApiBase(app);
        lastResponse   = RestProbe.of(apiBase).post(path, body);
    }

    /**
     * Renders the named template using global test-data and captured state (no bond list),
     * then POSTs to {@code {appApiBase}{path}}.
     *
     * <p>Use this for templates that don't reference bond-list fields, or for action
     * endpoints (quote, release) whose bodies are empty or contain only workflow state.
     *
     * <p>Path may contain {@code ${key}} tokens resolved from captured state, e.g.:
     * <pre>{@code
     * When I POST template "quote-inquiry" to app "blotter" path "/api/inquiry/${inquiry_id}/quote"
     * }</pre>
     */
    @When("I POST template {string} to app {string} path {string}")
    public void postTemplate(String template, String app, String path) {
        String body    = templateEngine.render(template);
        String apiBase = BBotRegistry.getConfig().getAppApiBase(app);
        lastResponse   = RestProbe.of(apiBase).post(path, body);
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    /**
     * Sends a GET request to {@code {appApiBase}{path}}.
     * {@code ${key}} tokens in the path are resolved from captured state.
     *
     * <p>Example:
     * <pre>{@code
     * When I GET from app "blotter" path "/api/inquiries"
     * When I GET from app "blotter" path "/api/inquiry/${inquiry_id}"
     * }</pre>
     */
    @When("I GET from app {string} path {string}")
    public void getFromApp(String app, String path) {
        String apiBase = BBotRegistry.getConfig().getAppApiBase(app);
        lastResponse   = RestProbe.of(apiBase).get(path);
    }

    // ── Status assertions ─────────────────────────────────────────────────────

    /**
     * Asserts the HTTP status code of the last response.
     */
    @Then("the response status should be {int}")
    public void responseStatusShouldBe(int expected) {
        requireLastResponse("the response status should be");
        lastResponse.assertStatus(expected);
    }

    // ── Field assertions ──────────────────────────────────────────────────────

    /**
     * Asserts that the JSON field at {@code jsonPath} equals {@code expected}.
     * Short-form paths ({@code "status"}) are normalised to {@code "$.status"}.
     *
     * <p>Example:
     * <pre>{@code
     * Then the response field "status" should be "PENDING"
     * Then the response field "$[0].isin" should be "US912828YJ02"
     * }</pre>
     */
    @Then("the response field {string} should be {string}")
    public void responseFieldShouldBe(String jsonPath, String expected) {
        requireLastResponse("the response field");
        lastResponse.assertField(jsonPath, expected);
    }

    /**
     * Asserts that the JSON field at {@code jsonPath} equals the value of
     * {@code fieldName} in the named bond list.
     *
     * <p>Example:
     * <pre>{@code
     * Then the response field "isin" should equal bond "HYPT_1" field "ISIN1"
     * }</pre>
     */
    @Then("the response field {string} should equal bond {string} field {string}")
    public void responseFieldShouldEqualBondField(String jsonPath, String bondList, String field) {
        requireLastResponse("the response field ... should equal bond");
        String expected = testData.resolveBondRef(bondList, field);
        lastResponse.assertField(jsonPath, expected);
    }

    /**
     * Asserts that the JSON field at {@code jsonPath} is non-null and non-blank.
     *
     * <p>Example:
     * <pre>{@code
     * Then the response field "inquiry_id" should not be empty
     * }</pre>
     */
    @Then("the response field {string} should not be empty")
    public void responseFieldShouldNotBeEmpty(String jsonPath) {
        requireLastResponse("the response field ... should not be empty");
        lastResponse.assertFieldNotEmpty(jsonPath);
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    /**
     * Captures the value of {@code jsonPath} from the last response into scenario state.
     * The alias is the last path segment, e.g. {@code "inquiry_id"} or {@code "$.inquiry_id"}
     * both store the value as {@code inquiry_id}.
     *
     * <p>The captured value is then available as {@code ${inquiry_id}} in subsequent
     * path strings and template substitutions.
     *
     * <p>Example:
     * <pre>{@code
     * And I capture the response field "inquiry_id"
     * When I POST template "quote-inquiry" to app "blotter" path "/api/inquiry/${inquiry_id}/quote"
     * }</pre>
     */
    @And("I capture the response field {string}")
    public void captureResponseField(String jsonPath) {
        requireLastResponse("I capture the response field");
        lastResponse.capture(jsonPath);
    }

    /**
     * Captures the value of {@code jsonPath} from the last response into scenario state
     * under an explicit {@code alias}.
     *
     * <p>Example:
     * <pre>{@code
     * And I capture the response field "inquiry_id" as "rfq-id"
     * When I POST template "quote-inquiry" to app "blotter" path "/api/inquiry/${rfq-id}/quote"
     * }</pre>
     */
    @And("I capture the response field {string} as {string}")
    public void captureResponseFieldAs(String jsonPath, String alias) {
        requireLastResponse("I capture the response field ... as");
        lastResponse.capture(jsonPath, alias);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void requireLastResponse(String stepName) {
        if (lastResponse == null)
            throw new AssertionError(
                "No REST response available for step '" + stepName + "'. " +
                "Ensure a GET or POST step runs before this assertion.");
    }
}
