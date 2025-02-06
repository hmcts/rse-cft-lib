@F-000
Feature: F-000 : Access Role Assignment API

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-000
  @FeatureToggle(get-ld-flag)
  Scenario: must access Role Assignment API
    Given a user with [an active caseworker profile],
    When a request is prepared with appropriate values,
    And the request [is to be made on behalf of Role Assignment API],
    And it is submitted to call the [Access Role Assignment API] operation of [Role Assignment API],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-120
  @FeatureToggle(LD:get-ld-flag=on)
  Scenario: must access Role Assignment API
    Given a user with [an active caseworker profile],
    When a request is prepared with appropriate values,
    And the request [is to be made on behalf of Role Assignment API],
    And it is submitted to call the [Access Role Assignment API] operation of [Role Assignment API],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-110
  @FeatureToggle(LD:get-ld-flag=off)
  Scenario: must access Role Assignment API
    Given a user with [an active caseworker profile],
    When a request is prepared with appropriate values,
    And the request [is to be made on behalf of Role Assignment API],
    And it is submitted to call the [Access Role Assignment API] operation of [Role Assignment API],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-000.04
  @FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
  Scenario: must access Role Assignment API
    Given a user with [an active caseworker profile],
    When a request is prepared with appropriate values,
    And the request [is to be made on behalf of Role Assignment API],
    And it is submitted to call the [Access Role Assignment API] operation of [Role Assignment API],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-000.05
  @FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=off)
  Scenario: must access Role Assignment API
    Given a user with [an active caseworker profile],
    When a request is prepared with appropriate values,
    And the request [is to be made on behalf of Role Assignment API],
    And it is submitted to call the [Access Role Assignment API] operation of [Role Assignment API],
    Then a positive response is received,
    And the response has all other details as expected.
