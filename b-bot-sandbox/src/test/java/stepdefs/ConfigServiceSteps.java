package stepdefs;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import com.bbot.core.registry.BBotRegistry;
import utils.ConfigServiceDsl;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for ConfigService.feature.
 *
 * <p>All Playwright-free — uses the JDK HTTP client via {@link ConfigServiceDsl}.
 * DSL is obtained from {@link BBotRegistry} so the base URL is environment-injected.
 */
public class ConfigServiceSteps {

    private final ConfigServiceDsl dsl =
            BBotRegistry.dsl("config-service", null, ConfigServiceDsl.class);

    // Shared state between When/Then steps
    private String lastNs;
    private String lastType;
    private String lastKey;

    // ── Precondition ──────────────────────────────────────────────────────────

    @Given("the config service is running")
    public void theConfigServiceIsRunning() {
        BBotRegistry.checkHealth("config-service");
    }

    // ── Namespace assertions ──────────────────────────────────────────────────

    @Then("the config service should list namespace {string}")
    public void configServiceShouldListNamespace(String ns) throws IOException, InterruptedException {
        dsl.assertNamespacePresent(ns);
    }

    // ── Read config ───────────────────────────────────────────────────────────

    @When("I read config {string} \\/ {string} \\/ {string}")
    public void iReadConfig(String ns, String type, String key)
            throws IOException, InterruptedException {
        lastNs   = ns;
        lastType = type;
        lastKey  = key;
        dsl.readConfig(ns, type, key); // validate 200
    }

    @Then("the config value {string} should be {string}")
    public void configValueShouldBe(String field, String expected)
            throws IOException, InterruptedException {
        String actual = dsl.getFieldValue(lastNs, lastType, lastKey, field);
        assertThat(actual)
                .as("Config [%s/%s/%s].%s", lastNs, lastType, lastKey, field)
                .isEqualTo(expected);
    }

    // ── Update config ─────────────────────────────────────────────────────────

    @When("I update config {string} \\/ {string} \\/ {string} setting {string} to {string}")
    public void iUpdateConfig(String ns, String type, String key, String field, String value)
            throws IOException, InterruptedException {
        lastNs   = ns;
        lastType = type;
        lastKey  = key;
        dsl.updateConfig(ns, type, key, field, value);
    }

    // ── Type listing ──────────────────────────────────────────────────────────

    @Then("the config service should list type {string} under namespace {string}")
    public void configServiceShouldListType(String type, String ns)
            throws IOException, InterruptedException {
        dsl.assertTypePresent(ns, type);
    }

    // ── Key listing (all entries under a type) ────────────────────────────────

    @When("I read all entries under {string} \\/ {string}")
    public void iReadAllEntries(String ns, String type) {
        lastNs   = ns;
        lastType = type;
        // state stored — assertions call DSL directly
    }

    @Then("the entry list should contain key {string}")
    public void entryListShouldContainKey(String key) throws IOException, InterruptedException {
        dsl.assertKeyPresent(lastNs, lastType, key);
    }

    // ── Delete config ─────────────────────────────────────────────────────────

    @When("I delete config {string} \\/ {string} \\/ {string}")
    public void iDeleteConfig(String ns, String type, String key)
            throws IOException, InterruptedException {
        lastNs   = ns;
        lastType = type;
        lastKey  = key;
        dsl.deleteConfig(ns, type, key);
    }

    @Then("the config entry should not exist")
    public void configEntryShouldNotExist() throws IOException, InterruptedException {
        dsl.assertEntryNotFound(lastNs, lastType, lastKey);
    }

    @Then("getting config {string} \\/ {string} \\/ {string} should return not found")
    public void gettingConfigShouldReturnNotFound(String ns, String type, String key)
            throws IOException, InterruptedException {
        dsl.assertEntryNotFound(ns, type, key);
    }
}
