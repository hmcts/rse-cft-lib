@F-003
Feature: F-003 : Delete Role Assignments by Role Assignment Id

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-031
  Scenario: must successfully delete single Role Assignment by Role Assignment Id
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains a Role Assignment Id],
    And it is submitted to call the [Delete Role Assignment by Assignment Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-032
  Scenario: must successfully delete Role Assignment without X-Corrlation-ID Header
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [does not have X-Corrlation-ID header],
    And the request [contains a Role Assignment Id],
    And it is submitted to call the [Delete Role Assignment by Assignment Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-033
  Scenario: must receive a positive response for a non-existing Role Assignment Id
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a non-existing Role Assignment Id],
    And it is submitted to call the [Delete Role Assignment by Assignment Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-034
  Scenario: must receive a positive when trying to delete a Role Assignment twice
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    And another successful call [to delete role assignments just created above] as in [S-034_DeleteDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains the same Assignment Id created above],
    And it is submitted to call the [Delete Role Assignment by Assignment Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.
