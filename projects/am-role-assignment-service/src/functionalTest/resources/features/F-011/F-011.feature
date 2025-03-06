@F-011
@FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
Feature: F-011 : Create Specific Role Assignments

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-210
  @FeatureToggle(RAS:iac_specific_1_0=on)
  Scenario: must successfully create specific access requested role for judiciary
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-210_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains specific-access-legal-ops case requested role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-210_DeleteDataForRoleAssignmentsForOrgRoles],
    And a successful call [to delete role assignments just created above] as in [S-210_DeleteDataForRoleAssignmentsForRequestedRole].

  @S-212
  @FeatureToggle(RAS:iac_specific_1_0=on)
  Scenario: must successfully create specific access denied by requester
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-212_Org_Role_Creation],
    And a successful call [to create role assignments for requested role] as in [S-212_Access_Requested],
    When a request is prepared with appropriate values,
    And the request [contains specific-access-judiciary case denied role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-212_DeleteDataForRoleAssignmentsForOrgRoles],

  @S-231
  @FeatureToggle(RAS:iac_specific_1_0=on)
  Scenario: must successfully create specific access granted role for CIVIL judiciary
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-231_Org_Role_Creation],
    And a successful call [to create role assignments for requested role] as in [S-231_Access_Requested],
    When a request is prepared with appropriate values,
    And the request [contains specific-access-judiciary case granted role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-231_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-232
  @FeatureToggle(RAS:iac_specific_1_0=on)
  Scenario: must successfully create specific access denied role for PRIVATELAW admin
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-232_Org_Role_Creation],
    And a successful call [to create role assignments for requested role] as in [S-232_Access_Requested],
    When a request is prepared with appropriate values,
    And the request [contains specific-access-admin case denied role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-232_DeleteDataForRoleAssignmentsForOrgRoles],
    And a successful call [to delete role assignments just created above] as in [S-232_DeleteDataForRoleAssignmentsForDeniedRole].
