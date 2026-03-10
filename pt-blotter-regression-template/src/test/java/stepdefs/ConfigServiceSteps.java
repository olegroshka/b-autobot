package com.bbot.template.stepdefs;

import io.cucumber.java.en.Then;
import com.bbot.template.utils.ConfigServiceDsl;

/**
 * Step definitions for Config Service scenarios.
 *
 * <p>REST-only — no Playwright page needed. Every step delegates to
 * {@link ConfigServiceDsl} which uses JDK {@code HttpClient} internally.
 */
public class ConfigServiceSteps {

    private final TestWorld world;
    private final ConfigServiceDsl configService;

    public ConfigServiceSteps(TestWorld world) {
        this.world = world;
        this.configService = world.session().dsl("config-service", null, ConfigServiceDsl.class);
    }

    @Then("the config namespace {string} should be present")
    public void configNamespaceShouldBePresent(String namespace) {
        configService.assertNamespacePresent(namespace);
    }

    @Then("the user {string} should have isAlgoTrader {string} in config service")
    public void userShouldHaveIsPTAdmin(String username, String expectedValue) {
        boolean expected = Boolean.parseBoolean(expectedValue);
        configService.assertUserIsAlgoTrader(username, expected);
    }

    @Then("the user from role {string} should have isAlgoTrader {string} in config service")
    public void userByRoleShouldHaveIsPTAdmin(String role, String expectedValue) {
        String username = world.session().getConfig().getTestData().getUser(role);
        boolean expected = Boolean.parseBoolean(expectedValue);
        configService.assertUserIsAlgoTrader(username, expected);
    }
}
