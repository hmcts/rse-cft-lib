@F-012
@FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
Feature: F-012 : Create Challenged Access Role for IA

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-213
  @FeatureToggle(RAS:iac_challenged_1_0=on)
  Scenario: must successfully create challenged-access-legal-operations role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-213_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains challenged-access-legal-operations case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.
    And a successful call [to delete role assignments just created above] as in [S-213_DeleteDataForRoleAssignmentsForOrgRoles],
    And a successful call [to delete role assignments just created above] as in [S-213_DeleteDataForRoleAssignmentsForChallengedAccess].

  @S-214
  @FeatureToggle(RAS:iac_challenged_1_0=on)
  Scenario: must successfully create challenged-access-judiciary role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-214_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains challenged-access-judiciary case requested role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.
    And a successful call [to delete role assignments just created above] as in [S-214_DeleteDataForRoleAssignmentsForOrgRoles],
    And a successful call [to delete role assignments just created above] as in [S-214_DeleteDataForRoleAssignmentsForChallengedAccess].

  @S-215
  @FeatureToggle(RAS:iac_challenged_1_0=on)
  Scenario: must successfully create challenged-access-admin role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-215_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains challenged-access-admin case requested role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.
    And a successful call [to delete role assignments just created above] as in [S-215_DeleteDataForRoleAssignmentsForOrgRoles],
    And a successful call [to delete role assignments just created above] as in [S-215_DeleteDataForRoleAssignmentsForChallengedAccess].
