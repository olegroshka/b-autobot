@blotter
Feature: PT-Blotter — Fixed Income Bond Portfolio Trading Blotter

  # ──────────────────────────────────────────────────────────────────────────────
  # M0 — Build pipeline spine
  # Goal: Playwright can open the blotter URL served by WireMock.
  # Quality gate: mvn verify -Dcucumber.filter.tags="@blotter and @smoke" → 1/1
  # ──────────────────────────────────────────────────────────────────────────────

  @smoke
  Scenario: PT-Blotter page loads
    Given the PT-Blotter is open
    Then the page title should contain "PT-Blotter"

  # ──────────────────────────────────────────────────────────────────────────────
  # M1 — Grid structure visible  [UNHIT — fails until Vite build runs]
  # Goal: AG Grid renders with all expected columns and seeded rows.
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
    And the grid should display column "sentPrice"
    And the grid should display column "sentSpread"
    And the grid should display column "status"

  @m1
  Scenario: Blotter loads with seeded inquiries
    Given the PT-Blotter is open
    Then the grid should have at least 5 rows
    And the row with ISIN "US912828YJ02" should have status "PENDING"
    And the row with ISIN "XS2346573523" should have status "PENDING"

  # ──────────────────────────────────────────────────────────────────────────────
  # M2 — Ticking reference prices  [UNHIT — fails until Vite build runs]
  # Goal: TW/CP+/CBBT price and spread cells update at ~400 ms; flash on each tick.
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
  # Goal: POST /api/inquiry adds a new PENDING row; unknown ISINs return 404.
  # Quality gate: mvn verify -Dcucumber.filter.tags="@m3" → 2/2 (no build needed)
  # ──────────────────────────────────────────────────────────────────────────────

  @m3 @api
  Scenario: Inquiry submitted via API appears in blotter as PENDING
    Given the PT-Blotter is open
    When a new inquiry is submitted for ISIN "US38141GXZ20" notional "3000000" side "BUY" client "SCHRODERS"
    Then the blotter API response status should be 201
    And the response should contain a non-blank "inquiry_id"

  @m3 @api
  Scenario: Unknown ISIN is rejected with 404
    Given the PT-Blotter is open
    When a new inquiry is submitted for ISIN "UNKNOWN-ISIN-XYZ"
    Then the blotter API response status should be 404

  # ──────────────────────────────────────────────────────────────────────────────
  # M4 — Toolbar: Ref Source / Ref Side / Markup ± / Units / APPLY
  #       [UNHIT — fails until Vite build runs]
  # Goal: APPLY computes price (units=c) or spread (units=bp) for selected rows
  #       using the chosen reference source, side, and markup offset.
  # Toolbar layout: [Source TW|CP+|CBBT] [Side Bid|Ask|Mid] [- markup +] [c|bp] [APPLY] [SEND]
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m4"
  # ──────────────────────────────────────────────────────────────────────────────

  @m4 @workflow
  Scenario: APPLY with units=c sets price from TW Mid reference
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN "US912828YJ02" should be a numeric value

  @m4 @workflow
  Scenario: APPLY with units=bp sets spread from CP+ Bid reference
    Given the PT-Blotter is open
    When I select the row with ISIN "XS2346573523"
    And I set the toolbar ref source "CP+" ref side "Bid" markup "0" units "bp"
    And I press APPLY
    Then the "spread" for ISIN "XS2346573523" should be a numeric value

  @m4 @workflow
  Scenario: Positive markup shifts price above mid
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0.5" units "c"
    And I press APPLY
    Then the "price" for ISIN "US912828YJ02" should be a numeric value

  @m4 @workflow
  Scenario: Unselected row is not affected by APPLY
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN "XS2346573523" should be blank

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
  # M5 — SEND: non-locking quote submission  [UNHIT — fails until Vite build runs]
  # Goal: SEND POSTs quote, stamps status=QUOTED, captures sentPrice/sentSpread.
  #       Row stays editable; re-APPLY → re-SEND updates the snapshot each time.
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m5"
  # ──────────────────────────────────────────────────────────────────────────────

  @m5 @workflow
  Scenario: SEND sets status to QUOTED
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN "US912828YJ02" should have status "QUOTED"

  @m5 @workflow
  Scenario: SEND captures sentPrice snapshot
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the "sentPrice" for ISIN "US912828YJ02" should be a numeric value

  @m5 @workflow
  Scenario: Re-quote — SEND again after re-APPLY updates sentPrice
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    And I set the toolbar ref source "TW" ref side "Mid" markup "0.5" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN "US912828YJ02" should have status "QUOTED"
    And the "sentPrice" for ISIN "US912828YJ02" should be a numeric value

  # ──────────────────────────────────────────────────────────────────────────────
  # M6 — Multi-row APPLY / SEND  [UNHIT — fails until Vite build runs]
  # Goal: APPLY and SEND operate on all checked rows simultaneously.
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m6"
  # ──────────────────────────────────────────────────────────────────────────────

  @m6 @multi
  Scenario: APPLY updates all selected rows
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I select the row with ISIN "XS2346573523"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    Then the "price" for ISIN "US912828YJ02" should be a numeric value
    And the "price" for ISIN "XS2346573523" should be a numeric value

  @m6 @multi
  Scenario: SEND quotes all selected rows
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I select the row with ISIN "XS2346573523"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN "US912828YJ02" should have status "QUOTED"
    And the row with ISIN "XS2346573523" should have status "QUOTED"

  @m6 @multi
  Scenario: Mix of Price and Spread across rows via two APPLY passes
    Given the PT-Blotter is open
    When I select the row with ISIN "US912828YJ02"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I select the row with ISIN "GB0031348658"
    And I set the toolbar ref source "CBBT" ref side "Bid" markup "0" units "bp"
    And I press APPLY
    Then the "price" for ISIN "US912828YJ02" should be a numeric value
    And the "spread" for ISIN "GB0031348658" should be a numeric value

  # ──────────────────────────────────────────────────────────────────────────────
  # M7 — DSL: end-to-end re-quote workflow  [UNHIT — fails until Vite build runs]
  # Goal: Full APPLY → SEND → re-APPLY → re-SEND cycle via composed step phrases.
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m7"
  # ──────────────────────────────────────────────────────────────────────────────

  @m7 @dsl
  Scenario: Full re-quote workflow end-to-end
    Given the PT-Blotter is open
    When I select the row with ISIN "GB0031348658"
    And I set the toolbar ref source "TW" ref side "Mid" markup "0" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN "GB0031348658" should have status "QUOTED"
    And the "sentPrice" for ISIN "GB0031348658" should be a numeric value
    # Re-quote: change markup and re-SEND without selecting again
    When I set the toolbar ref source "CP+" ref side "Ask" markup "-0.25" units "c"
    And I press APPLY
    And I press SEND
    Then the row with ISIN "GB0031348658" should have status "QUOTED"
    And the "sentPrice" for ISIN "GB0031348658" should be a numeric value

  # ──────────────────────────────────────────────────────────────────────────────
  # M8 — RELEASE PT access control + workflow  [UNHIT — fails until Vite build runs]
  # Goal: RELEASE PT button respects isPTAdmin from config service;
  #       admin can move rows to RELEASED status.
  # Quality gate: mvn verify -Dblotter.build.skip=false -Dcucumber.filter.tags="@m8"
  # ──────────────────────────────────────────────────────────────────────────────

  @m8 @access
  Scenario: RELEASE PT button is not accessible when doej isPTAdmin flag is false
    Given the PT-Blotter is open as user "doej"
    Then the RELEASE PT button should be disabled

  @m8 @access
  Scenario: RELEASE PT button is accessible when smithj isPTAdmin flag is true
    Given the PT-Blotter is open as user "smithj"
    Then the RELEASE PT button should be enabled

  @m8 @workflow
  Scenario: smithj releases a row — status becomes RELEASED
    Given the PT-Blotter is open as user "smithj"
    When I select the row with ISIN "US912828YJ02"
    And I press RELEASE PT
    Then the row with ISIN "US912828YJ02" should have status "RELEASED"

  @m8 @workflow
  Scenario: Release PT does not affect rows not selected by smithj
    Given the PT-Blotter is open as user "smithj"
    When I select the row with ISIN "US912828YJ02"
    And I press RELEASE PT
    Then the row with ISIN "XS2346573523" should have status "PENDING"

  @m8 @workflow
  Scenario: smithj releases multiple rows simultaneously
    Given the PT-Blotter is open as user "smithj"
    When I select the row with ISIN "US912828YJ02"
    And I select the row with ISIN "XS2346573523"
    And I press RELEASE PT
    Then the row with ISIN "US912828YJ02" should have status "RELEASED"
    And the row with ISIN "XS2346573523" should have status "RELEASED"
