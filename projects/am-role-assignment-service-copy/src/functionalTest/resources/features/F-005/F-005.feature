@F-005
Feature: F-005 : Get Role Assignments by Actor Id

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-051
  Scenario: must successfully receive single Role Assignment by Actor Id
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains an Actor Id having only single Role Assignment],
    And it is submitted to call the [Get Role Assignments by Actor Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-005_DeleteDataForMultipleRoleAssignments].

  @S-052
  Scenario: must successfully receive multiple Role Assignments by Actor Id
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-052_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains an Actor Id having multiple Role Assignments],
    And it is submitted to call the [Get Role Assignments by Actor Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-005_DeleteDataForMultipleRoleAssignments].

  @S-053
  Scenario: should return a blank response for a non-existing ActorId
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a non-existing Actor Id],
    And it is submitted to call the [Get Role Assignments by Actor Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-054
  Scenario: must successfully receive Role Assignments without X-Correlation-ID Header
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [does not have X-Correlation-ID header],
    And the request [contains an Actor Id having only single Role Assignment],
    And it is submitted to call the [Get Role Assignments by Actor Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-005_DeleteDataForMultipleRoleAssignments].

  @S-055
  Scenario: must receive an error response for an invalid ActorId
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains an invalid Actor Id],
    And it is submitted to call the [Get Role Assignments by Actor Id] operation of [Role Assignment Service],
    Then a negative response is received,
    And the response has all other details as expected.

  @S-056
  Scenario: must successfully receive response for Role Assignments without If-None-Match Header
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [does not contain If-None-Match header],
    And the request [contains an Actor Id having only single Role Assignment],
    And it is submitted to call the [Get Role Assignments by Actor Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-005_DeleteDataForMultipleRoleAssignments].

  @S-057
  Scenario: must successfully receive response for Role Assignments with If-None-Match Header having older Etag version
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains If-None-Match header with older Etag version],
    And the request [contains an Actor Id having only single Role Assignment],
    And it is submitted to call the [Get Role Assignments by Actor Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-005_DeleteDataForMultipleRoleAssignments].

  @S-058
  Scenario: must receive a negative 'Not Modified' response If-None-Match Header having Latest Etag version
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    And another successful call [to get the role assignment for an actor] as in [S-058_GetDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains If-None-Match header with latest Etag version],
    And the request [contains an Actor Id having only single Role Assignment],
    And it is submitted to call the [Get Role Assignments by Actor Id] operation of [Role Assignment Service],
    Then a negative response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-005_DeleteDataForMultipleRoleAssignments].


  @S-059
  Scenario: must successfully receive single Role Assignment created with actorId having only numbers.
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-059_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains an Actor Id having only single Role Assignment],
    And it is submitted to call the [Get Role Assignments by Actor Id] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-005_DeleteDataForMultipleRoleAssignments].
