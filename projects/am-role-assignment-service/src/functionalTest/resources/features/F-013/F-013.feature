@F-013
@FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
Feature: F-013 : Create CIVIL Challenged Access Role Assignments

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-216
  @FeatureToggle(RAS:iac_challenged_1_0=on)
  Scenario: must successfully create challenged-access-judiciary role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-216_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains challenged-access-judiciary case requested role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.
    And a successful call [to delete role assignments just created above] as in [S-216_DeleteDataForRoleAssignmentsForOrgRoles],
    And a successful call [to delete role assignments just created above] as in [S-216_DeleteDataForRoleAssignmentsForChallengedAccess].
