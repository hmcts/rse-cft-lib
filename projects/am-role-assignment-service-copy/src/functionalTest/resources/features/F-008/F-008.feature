@F-008
@FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
Feature: F-008 : Create And Replace Staff Role Assignments for IAC

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-103
  Scenario: must successfully create multiple Role Assignments for staff org roles
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments with replace existing set to false],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

    #create 1,2 first and then pass 2,3 later so 2,3 will be retained.
  @S-104
  Scenario: must successfully create & replace multiple Role Assignments for staff org roles
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create some org role assignments for an actor] as in [S-104_Multiple_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have same process and Reference values],
    And the request [contains multiple Role Assignments just created and couple of new role assignments],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-105
  Scenario: must successfully remove multiple Role Assignments for staff org roles
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create some org role assignments for an actor] as in [S-104_Multiple_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have same process and Reference values],
    And the request [contains empty requested roles list],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.

  @S-106
  Scenario: must successfully create multiple Role Assignments for staff case role.
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-106_Multiple_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains multiple case Role Assignments for the same actors],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-106_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-107
  Scenario: must successfully create & replace multiple Role Assignments for staff case role.
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-106_Multiple_Org_Role_Creation],
    And a successful call [to create multiple case role assignments for the same actors] as in [S-107_Multiple_Case_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and reference set to caseId],
    And the request [contains multiple Role Assignments just created and couple of new role assignments],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-106_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-108
  Scenario: must successfully delete all existing Role Assignments for an actor having staff case roles.
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-106_Multiple_Org_Role_Creation],
    And a successful call [to create multiple case role assignments for the same actors] as in [S-107_Multiple_Case_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and reference set to caseId],
    And the request [contains empty Role Assignments list],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-106_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-154
  Scenario: must successfully delete all existing Role Assignments for an actor having staff case roles.
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-106_Multiple_Org_Role_Creation],
    And a successful call [to create multiple case role assignments for the same actors] as in [S-107_Multiple_Case_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and reference set to caseId],
    And the request [contains empty Role Assignments list],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [S-106_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-201
  @FeatureToggle(RAS:iac_1_1=on)
  Scenario: must successfully create case-manager Case Role Assignment
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-201_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains case-manager case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-201_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-207
  @FeatureToggle(RAS:iac_1_1=on)
  Scenario: must successfully create case-allocator Case Role Assignment
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-207_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains case-allocator case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-207_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-208
  @FeatureToggle(RAS:iac_1_1=on)
  Scenario: must successfully create conflict-of-interest Case Role Assignment for self
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta2 - who is the actor for requested role],
    And a user [Befta2 - who is the requester for requested role],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains conflict-of-interest case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to create case-allocator org role for the requester] as in [S-208_Org_Role_Creation_For_Requester],
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-208_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-209
  @FeatureToggle(RAS:iac_1_1=on)
  Scenario: must successfully create conflict-of-interest Case Role Assignment for other Actor
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the requester for requested role],
    And a successful call [to create case-allocator org role for the requester] as in [S-209_Org_Role_Creation_For_Requester],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains conflict-of-interest case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-209_DeleteDataForRoleAssignmentsForOrgRoles].
