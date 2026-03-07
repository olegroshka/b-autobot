package runners;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * JUnit 5 Platform Suite runner for Cucumber.
 *
 * <p>Configuration is also driven by {@code src/test/resources/cucumber.properties}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "stepdefs")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-reports/report.html, " +
                "json:target/cucumber-reports/report.json"
)
public class TestRunner {
    // Intentionally empty — JUnit Platform Suite discovers scenarios automatically.
}
