@F-018
@FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
Feature: F-018 : Create Case Role Assignments for Employment Tribunal

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-261
  Scenario: must successfully create hearing-judge ET_EnglandWales case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-261_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains hearing-judge case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-261_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-261_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-262
  Scenario: must successfully create lead-judge ET_Scotland case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-262_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains lead-judge case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-262_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-262_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-263
  Scenario: must successfully create allocated-tribunal-caseworker ET_Scotland case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-263_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-tribunal-caseworker case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-263_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-263_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-264
  Scenario: must successfully create allocated-admin-caseworker ET_Scotland case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-264_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-admin-caseworker case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-264_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-264_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-265
  Scenario: must successfully create allocated-ctsc-caseworker ET_Scotland case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-265_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-ctsc-caseworker case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-265_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-265_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-266
  Scenario: must successfully create tribunal-member-1 ET_EnglandWales case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-266_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains tribunal-member-1 case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-266_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-266_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-267
  Scenario: must successfully create tribunal-member-2 ET_EnglandWales case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-267_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains tribunal-member-2 case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-267_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-267_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-268
  Scenario: must successfully create challenged-access-legal-ops role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-268_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains challenged-access-legal-ops case requested role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected.
    And a successful call [to delete role assignments just created above] as in [S-268_DeleteDataForRoleAssignmentsForOrgRoles],
    And a successful call [to delete role assignments just created above] as in [S-268_DeleteDataForRoleAssignmentsForChallengedAccess].

  @S-269
  Scenario: must successfully create allocated-tribunal-caseworker ET_Scotland_Multiple case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-269_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-tribunal-caseworker case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-269_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-269_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-270
  Scenario: must successfully create allocated-admin-caseworker ET_EnglandWales_Multiple case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-270_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-admin-caseworker case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-270_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-270_DeleteDataForRoleAssignmentsForOrgRoles].
