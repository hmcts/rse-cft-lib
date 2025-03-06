@F-006
Feature: F-006 : Post Role Assignments Query Request

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-081
  Scenario: must successfully receive Role Assignments with one query param
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains one search query param]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-006_DeleteDataForMultipleRoleAssignments].

  @S-082
  Scenario: must successfully receive Role Assignments with more than one query params
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains more than one query params]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-006_DeleteDataForMultipleRoleAssignments].

  @S-083
  Scenario: must successfully receive Role Assignments without specific page number
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-083_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments without specific page number]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-083_DeleteDataForMultipleRoleAssignments].

  @S-084
  Scenario: must successfully receive Role Assignments with specific page number
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-083_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments with specific page number],
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-083_DeleteDataForMultipleRoleAssignments].

  @S-085
  Scenario: must successfully receive Role Assignments without X-Correlation-ID Header
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-083_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [does not have X-Correlation-ID header],
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-083_DeleteDataForMultipleRoleAssignments].

  @S-086
  Scenario: must successfully receive Role Assignments without optional headers
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [does not have size header],
    And the request [does not have sort header],
    And the request [does not have direction header],
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-006_DeleteDataForMultipleRoleAssignments].

  @S-087
  Scenario: must successfully receive Role Assignments with optional headers
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-087_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [has size header],
    And the request [has sort header],
    And the request [has direction header],
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-087_DeleteDataForMultipleRoleAssignments].

  @S-088
  Scenario: must successfully receive Role Assignments including role label with one query param
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains one search query param]
    And the request [has includeLabels request param set to true]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [F-006_DeleteDataForMultipleRoleAssignments].
