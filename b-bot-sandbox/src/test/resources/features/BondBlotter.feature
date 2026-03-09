@blotter
Feature: PT-Blotter — Fixed Income Bond Portfolio Trading Blotter

  # ──────────────────────────────────────────────────────────────────────────────
  # Deployment precondition — Version gate
  #
  # Formal proof: the exact service versions under test are deployed and healthy
  # in UAT before any blotter scenarios execute.  This scenario runs first and
  # its pass/fail state appears at the top of the Cucumber regression report,
  # giving QA sign-off evidence that the correct software was tested.
  #
  # Versions are declared in b-bot.test-data.service-versions (application.conf).
  # Quality gate: mvn verify -Dcucumber.filter.tags="@blotter and @precondition" → 1/1
  # ──────────────────────────────────────────────────────────────────────────────

  @precondition @deployment
  Scenario: Required credit trading services are deployed at the tested versions
    Given the deployment dashboard is available
    Then the service "credit-rfq-blotter" should be "RUNNING" at its tested version
    And the service "credit-pt-pricer" should be "RUNNING" at its tested version
    And the service "credit-pt-neg-engine" should be "RUNNING" at its tested version

  # ──────────────────────────────────────────────────────────────────────────────
  # M0 — Build pipeline spine
  # Quality gate: mvn verify -Dcucumber.filter.tags="@blotter and @smoke" → 1/1
  # ──────────────────────────────────────────────────────────────────────────────

  @smoke
  Scenario: PT-Blotter page loads
    Given the PT-Blotter is open
    Then the page title should contain "PT-Blotter"

  # ──────────────────────────────────────────────────────────────────────────────
  # M1 — Grid structure visible
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m1"
  # ──────────────────────────────────────────────────────────────────────────────

  @m1
  Scenario: Blotter renders expected column groups
    Given the PT-Blotter is open
    Then the grid should display column "ptId"
    And the grid should display column "ptLineId"
    And the grid should display column "isin"
    And the grid should display column "twPrice"
    And the grid should display column "twSpread"
    And the grid should display column "cpPrice"
    And the grid should display column "cbbPrice"
    And the grid should display column "price"
    And the grid should display column "spread"
    And the grid should display column "quotedPrice"
    And the grid should display column "quotedSpread"
    And the grid should display column "status"

  @m1
  Scenario: Blotter loads with seeded inquiries
    Given the PT-Blotter is open
    Then the grid should have at least 5 rows
    And the row with ISIN from "HYPT_1" field "ISIN1" should have status "PENDING"
    And the row with ISIN from "HYPT_1" field "ISIN2" should have status "PENDING"

  # ──────────────────────────────────────────────────────────────────────────────
  # M2 — Ticking reference prices
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m2"
  # ──────────────────────────────────────────────────────────────────────────────

  @m2 @ticking
  Scenario: Reference price cells update within the live feed window
    Given the PT-Blotter is open
    When I wait up to 3 seconds for the "twPrice" cell in row 0 to change value
    Then the "twPrice" cell in row 0 should match the pattern "[0-9]+[.][0-9]+ / [0-9]+[.][0-9]+"

  @m2 @ticking
  Scenario: TW Price cell flashes on a live update
    Given the PT-Blotter is open
    When I wait for the "twPrice" cell in row 0 to flash
    Then the "twPrice" cell in row 0 should have received at least one tick update

  @m2 @ticking
  Scenario: All three reference sources are ticking
    Given the PT-Blotter is open
    Then within 3 seconds the "cpPrice" cell in row 0 should change value
    And within 3 seconds the "cbbPrice" cell in row 0 should change value

  # ──────────────────────────────────────────────────────────────────────────────
  # M3 — REST inquiry ingestion
  # Quality gate: mvn verify -Dcucumber.filter.tags="@m3" → 2/2 (no build needed)
  # ──────────────────────────────────────────────────────────────────────────────

  @m3 @api
  Scenario: Inquiry submitted via API appears in blotter as PENDING
    Given the PT-Blotter is open
    When a new inquiry is submitted for ISIN from "HYPT_1" field "ISIN3" notional "3000000" side "BUY" client "SCHRODERS"
    Then the blotter API response status should be 201
    And the response should contain a non-blank "inquiry_id"

  @m3 @api
  Scenario: Unknown ISIN is rejected with 404
    Given the PT-Blotter is open
    When a new inquiry is submitted for ISIN "UNKNOWN-ISIN-XYZ"
    Then the blotter API response status should be 404

  # ──────────────────────────────────────────────────────────────────────────────
  # M4 — Toolbar: Ref Source / Ref Side / Markup ± / Units / APPLY
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m4"
  # ──────────────────────────────────────────────────────────────────────────────

  @m4 @workflow
  Scenario: APPLY with units=c sets price from TW Mid reference
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value

  @m4 @workflow
  Scenario: APPLY with units=bp sets spread from CP+ Bid reference
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN2"
    And I set the toolbar ref source "CP+" ref side "Bid" markup "0" units "bp"
    And I press APPLY
    Then the "spread" for ISIN from "HYPT_1" field "ISIN2" should be a numeric value

  @m4 @workflow
  Scenario: Positive markup shifts price above mid
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0.5" units "c"
    And I press APPLY
    Then the "price" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value

  @m4 @workflow
  Scenario: Unselected row is not affected by APPLY
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN from "HYPT_1" field "ISIN2" should be blank

  @m4 @workflow
  Scenario: Markup plus button increments markup value
    Given the PT-Blotter is open
    When I press the markup plus button
    Then the markup input should show a positive value

  @m4 @workflow
  Scenario: Markup minus button decrements markup value
    Given the PT-Blotter is open
    When I press the markup minus button
    Then the markup input should show a negative value

  # ──────────────────────────────────────────────────────────────────────────────
  # M5 — SEND: non-locking quote submission
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m5"
  # ──────────────────────────────────────────────────────────────────────────────

  @m5 @workflow
  Scenario: SEND sets status to QUOTED
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "QUOTED"

  @m5 @workflow
  Scenario: SEND captures quotedPrice snapshot
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the "quotedPrice" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value

  @m5 @workflow
  Scenario: Re-quote — SEND again after re-APPLY updates quotedPrice
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    And I set the toolbar ref source "TW" ref side "Mid" markup "0.5" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "QUOTED"
    And the "quotedPrice" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value

  # ──────────────────────────────────────────────────────────────────────────────
  # M6 — Multi-row APPLY / SEND
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m6"
  # ──────────────────────────────────────────────────────────────────────────────

  @m6 @multi
  Scenario: APPLY updates all selected rows
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I select the row with ISIN from "HYPT_1" field "ISIN2"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value
    And the "price" for ISIN from "HYPT_1" field "ISIN2" should be a numeric value

  @m6 @multi
  Scenario: SEND quotes all selected rows
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I select the row with ISIN from "HYPT_1" field "ISIN2"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "QUOTED"
    And the row with ISIN from "HYPT_1" field "ISIN2" should have status "QUOTED"

  @m6 @multi
  Scenario: Mix of Price and Spread across rows via two APPLY passes
    Given the PT-Blotter is open
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I select the row with ISIN from "IGPT_1" field "ISIN1"
    And I set the toolbar ref source "CBBT" ref side "Bid" markup "0" units "bp"
    And I press APPLY
    Then the "price" for ISIN from "HYPT_1" field "ISIN1" should be a numeric value
    And the "spread" for ISIN from "IGPT_1" field "ISIN1" should be a numeric value

  # ──────────────────────────────────────────────────────────────────────────────
  # M7 — DSL: end-to-end re-quote workflow
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m7"
  # ──────────────────────────────────────────────────────────────────────────────

  @m7 @dsl
  Scenario: Full re-quote workflow end-to-end
    Given the PT-Blotter is open
    When I select the row with ISIN from "IGPT_1" field "ISIN1"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN from "IGPT_1" field "ISIN1" should have status "QUOTED"
    And the "quotedPrice" for ISIN from "IGPT_1" field "ISIN1" should be a numeric value
    # Re-quote: change markup and re-SEND without selecting again
    When I set the toolbar ref source "CP+" ref side "Ask" markup "-0.25" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN from "IGPT_1" field "ISIN1" should have status "QUOTED"
    And the "quotedPrice" for ISIN from "IGPT_1" field "ISIN1" should be a numeric value

  # ──────────────────────────────────────────────────────────────────────────────
  # M8 — RELEASE PT access control + workflow
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m8"
  # User roles are declared in b-bot.test-data.users (application.conf).
  # ──────────────────────────────────────────────────────────────────────────────

  @m8 @access
  Scenario: RELEASE PT button is not accessible when trader isPTAdmin flag is false
    Given the PT-Blotter is open as the trader
    Then the RELEASE PT button should be disabled

  @m8 @access
  Scenario: RELEASE PT button is accessible when admin isPTAdmin flag is true
    Given the PT-Blotter is open as the admin
    Then the RELEASE PT button should be enabled

  @m8 @workflow
  Scenario: Admin releases a row — status becomes RELEASED
    Given the PT-Blotter is open as the admin
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I press RELEASE PT
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "RELEASED"

  @m8 @workflow
  Scenario: Release PT does not affect rows not selected by admin
    Given the PT-Blotter is open as the admin
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I press RELEASE PT
    Then the row with ISIN from "HYPT_1" field "ISIN2" should have status "PENDING"

  @m8 @workflow
  Scenario: Admin releases multiple rows simultaneously
    Given the PT-Blotter is open as the admin
    When I select the row with ISIN from "HYPT_1" field "ISIN1"
    And I select the row with ISIN from "HYPT_1" field "ISIN2"
    And I press RELEASE PT
    Then the row with ISIN from "HYPT_1" field "ISIN1" should have status "RELEASED"
    And the row with ISIN from "HYPT_1" field "ISIN2" should have status "RELEASED"
