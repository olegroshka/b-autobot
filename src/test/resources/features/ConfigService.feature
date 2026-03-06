@config-service
Feature: Config Service — in-memory configuration store

  # Quality gate: mvn verify -Dcucumber.filter.tags="@config-service and @smoke" → 2/2
  # Quality gate: mvn verify -Dcucumber.filter.tags="@config-service and @api"   → 12/12
  # Quality gate: mvn verify -Dcucumber.filter.tags="@config-service"            → 14/14

  # ── Namespace discovery ─────────────────────────────────────────────────────

  @smoke
  Scenario: Config service lists all seeded namespaces
    Given the config service is running
    Then the config service should list namespace "credit.pt.access"
    And the config service should list namespace "credit.booking"
    And the config service should list namespace "credit.risk"
    And the config service should list namespace "market.data"

  @smoke
  Scenario: Type listed under credit.pt.access namespace
    Given the config service is running
    Then the config service should list type "Permissions" under namespace "credit.pt.access"

  # ── Permissions — all four seed users ──────────────────────────────────────

  @api
  Scenario: All four seed users are present in Permissions config
    Given the config service is running
    When I read all entries under "credit.pt.access" / "Permissions"
    Then the entry list should contain key "doej"
    And the entry list should contain key "smithj"
    And the entry list should contain key "patelv"
    And the entry list should contain key "nguyenl"

  @api
  Scenario: doej is not a PT admin by default
    Given the config service is running
    When I read config "credit.pt.access" / "Permissions" / "doej"
    Then the config value "isPTAdmin" should be "false"

  @api
  Scenario: smithj is a PT admin by default
    Given the config service is running
    When I read config "credit.pt.access" / "Permissions" / "smithj"
    Then the config value "isPTAdmin" should be "true"

  @api
  Scenario: patelv is not a PT admin by default
    Given the config service is running
    When I read config "credit.pt.access" / "Permissions" / "patelv"
    Then the config value "isPTAdmin" should be "false"

  @api
  Scenario: nguyenl is a PT admin by default
    Given the config service is running
    When I read config "credit.pt.access" / "Permissions" / "nguyenl"
    Then the config value "isPTAdmin" should be "true"

  # ── Other namespaces ────────────────────────────────────────────────────────

  @api
  Scenario: Read booking settings
    Given the config service is running
    When I read config "credit.booking" / "Settings" / "default"
    Then the config value "autoBook" should be "false"
    And the config value "bookingDesk" should be "FIXED_INCOME"

  @api
  Scenario: Read risk limits
    Given the config service is running
    When I read config "credit.risk" / "Limits" / "default"
    Then the config value "maxNotional" should be "50000000"
    And the config value "alertThreshold" should be "0.9"

  @api
  Scenario: Read market data sources
    Given the config service is running
    When I read config "market.data" / "Sources" / "default"
    Then the config value "primary" should be "TW"
    And the config value "fallback" should be "CP+"

  # ── Mutable config — update and read back ──────────────────────────────────

  @api
  Scenario: Promote doej to PT admin and restore
    Given the config service is running
    When I update config "credit.pt.access" / "Permissions" / "doej" setting "isPTAdmin" to "true"
    Then the config value "isPTAdmin" should be "true"
    # Reset — avoid polluting other tests
    When I update config "credit.pt.access" / "Permissions" / "doej" setting "isPTAdmin" to "false"
    Then the config value "isPTAdmin" should be "false"

  # ── Create and delete entries ───────────────────────────────────────────────

  @api
  Scenario: Create a new user entry via PUT then delete it
    Given the config service is running
    When I update config "credit.pt.access" / "Permissions" / "jonesb" setting "isPTAdmin" to "false"
    Then the config value "isPTAdmin" should be "false"
    When I delete config "credit.pt.access" / "Permissions" / "jonesb"
    Then the config entry should not exist

  @api
  Scenario: Deleting a non-existent entry returns not found
    Given the config service is running
    Then getting config "credit.pt.access" / "Permissions" / "nobody" should return not found
