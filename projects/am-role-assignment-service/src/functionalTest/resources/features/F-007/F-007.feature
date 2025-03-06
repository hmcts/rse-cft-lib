@F-007
Feature: F-007 : Get Static List of Roles Configuration

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-071
  Scenario: must successfully receive static list of roles
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And it is submitted to call the [Get Static List of Roles Configuration] operation of [Role Assignment Service],
    Then a positive response is received.

#  The content is not required for for Get operation.
#  @S-072
#  Scenario: must receive an error response when content-type other than application/json
#    Given a user with [an active caseworker profile with full permissions],
#    When a request is prepared with appropriate values,
#    And the request [contains content-type of application/xml],
#    And it is submitted to call the [Get Static List of Roles] operation of [Role Assignment Service],
#    Then a negative response is received,
#    And the response has all other details as expected.
