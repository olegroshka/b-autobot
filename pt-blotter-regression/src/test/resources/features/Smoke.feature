@smoke
Feature: PT-Blotter smoke — verifies the blotter loads and grid renders

  # Run against DevServer:
  #   cd pt-blotter-regression
  #   mvn verify -Db-bot.env=devserver -Dcucumber.filter.tags="@smoke"
  #
  # Run against UAT (once application-uat.conf is populated):
  #   mvn verify -Db-bot.env=uat -Dcucumber.filter.tags="@smoke"

  @smoke
  Scenario: Blotter opens and grid renders
    Given the PT-Blotter is open
    Then the blotter grid should be visible
