@F-016
@FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
Feature: F-016 : Create Case Role Assignments for PublicLaw

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-241
  Scenario: must successfully create hearing-judge case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-241_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains hearing-judge case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-241_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-241_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-242
  Scenario: must successfully create allocated-magistrate case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-242_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-magistrate role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-242_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-242_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-243
  Scenario: must successfully create allocated-legal-adviser case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-243_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-legal-adviser role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-243_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-243_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-244
  Scenario: must successfully create hearing-legal-adviser case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-244_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains hearing-legal-adviser role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-244_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-244_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-245
  Scenario: must successfully create allocated-judge case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-245_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-judge case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-245_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-245_DeleteDataForRoleAssignmentsForOrgRoles].
