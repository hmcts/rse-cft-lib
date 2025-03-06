@F-009
Feature: F-009 : Post Role Assignments Advance Query Request

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-183
  Scenario: must successfully receive Role Assignments without specific page number
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-183_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments without specific page number]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-183_DeleteDataForMultipleRoleAssignments].

  @S-184
  Scenario: must successfully receive Role Assignments with specific page number
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-183_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments with specific page number],
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-183_DeleteDataForMultipleRoleAssignments].

  @S-185
  Scenario: must successfully receive Role Assignments with page size including role label
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-185_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments with a page size]
    And the request [has includeLabels request param set to true]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-185_DeleteDataForMultipleRoleAssignments].

  @S-186
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

  @S-187
  Scenario: must successfully receive Role Assignments with optional headers
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-187_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [has size header],
    And the request [has sort header],
    And the request [has direction header],
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-187_DeleteDataForMultipleRoleAssignments].

  @S-172
  Scenario: must successfully receive Role Assignments with list of multiple queries
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create org role assignments for actors & requester] as in [S-106_Multiple_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains list of multiple search queries]
    And the request [consider the OR operation between search queries]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-106_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-173
  Scenario: must successfully receive Role Assignments with has_attributes
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-106_Multiple_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains list of single search query with has_attributes]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-106_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-174
  Scenario: must successfully receive Read Only Role Assignments
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create org role assignments for actors & requester] as in [S-106_Multiple_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains list of single search query with readonly false]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-106_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-175
  Scenario: must successfully receive Role Assignments with null attributes
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-175_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains list of single search query with null attribute]
    And it is submitted to call the [Post Role Assignments Query Request] operation of [Role Assignment Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-175_DeleteDataForMultipleRoleAssignments].

