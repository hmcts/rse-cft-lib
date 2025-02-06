@F-002
Feature: F-002 : Delete Role Assignments by Process and Reference

  Background:
    Given an appropriate test context as detailed in the test data source

#  @S-021 @Ignore # This end-point is currently disabled.
#  Scenario: must successfully delete single Role Assignment by Actor Id
#    Given a user with [an active IDAM profile with full permissions],
#    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
#    When a request is prepared with appropriate values,
#    And the request [contains an Actor Id having only single Role Assignment],
#    And it is submitted to call the [Delete Role Assignments by Actor Id] operation of [Role Assignment Service],
#    Then a positive response is received,
#    And the response has all other details as expected.
#
#  @S-022 @Ignore # This end-point is currently disabled.
#  Scenario: must successfully delete multiple Role Assignments by Actor Id
#    Given a user with [an active IDAM profile with full permissions],
#    And a successful call [to create multiple role assignment for an actor] as in [S-022_CreationDataForRoleAssignment],
#    When a request is prepared with appropriate values,
#    And the request [contains an Actor Id having multiple Role Assignments],
#    And it is submitted to call the [Delete Role Assignments by Actor Id] operation of [Role Assignment Service],
#    Then a positive response is received,
#    And the response has all other details as expected.
#
#  @S-023 @Ignore # This end-point is currently disabled.
#  Scenario: must successfully delete Role Assignment without X-Corrlation-ID Header
#    Given a user with [an active IDAM profile with full permissions],
#    And a successful call [to create a role assignment for an actor] as in [S-021_CreationDataForRoleAssignment],
#    When a request is prepared with appropriate values,
#    And the request [does not have X-Correlation-ID header],
#    And the request [contains an Actor Id having only single Role Assignment],
#    And it is submitted to call the [Delete Role Assignments by Actor Id] operation of [Role Assignment Service],
#    Then a positive response is received,
#    And the response has all other details as expected.
#
#  @S-024 @Ignore # This end-point is currently disabled.
#  Scenario: must receive an error response for a non-existing ActorId
#    Given a user with [an active IDAM profile with full permissions],
#    When a request is prepared with appropriate values,
#    And the request [contains a non-existing Actor Id],
#    And it is submitted to call the [Delete Role Assignments by Actor Id] operation of [Role Assignment Service],
#    Then a negative response is received,
#    And the response has all other details as expected.

  @S-025
  Scenario: must successfully delete single Role Assignment by Process
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains Process and Reference associated with single Role Assignment],
    And it is submitted to call the [Delete Role Assignments by Process] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-026
  Scenario: must successfully delete multiple Role Assignments by Process
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create multiple role assignment for an actor] as in [S-026_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains Process and Reference associated with multiple Role Assignments],
    And it is submitted to call the [Delete Role Assignments by Process] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-027
  Scenario: must receive positive response when delete Role Assignment with a non-existing Process
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a non-existing Process],
    And it is submitted to call the [Delete Role Assignments by Process] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-028
  Scenario: must receive positive response when delete Role Assignment with a non-existing Reference
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a non-existing Reference],
    And it is submitted to call the [Delete Role Assignments by Process] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-029
  Scenario: must successfully delete Role Assignment with Assigner Id Header
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains a valid Assigner Id header],
    And the request [contains Process and Reference associated with single Role Assignment],
    And it is submitted to call the [Delete Role Assignments by Process] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-030
  Scenario: must receive positive response when trying to delete a Role Assignment twice
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    And another successful call [to delete role assignments just created above] as in [S-030_DeleteDataForRoleAssignments],
    When a request is prepared with appropriate values,
    And the request [contains the same Process and Reference created above],
    And it is submitted to call the [Delete Role Assignments by Process] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-035
  Scenario: must successfully delete single CCD Role Assignment by Process
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignmentByServiceId],
    When a request is prepared with appropriate values,
    And the request [contains Process and Reference associated with single Role Assignment],
    And it is submitted to call the [Delete Role Assignments by Process] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.
