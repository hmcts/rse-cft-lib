@F-015
@FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
Feature: F-015 : Create Case Role Assignments for Privatelaw

  Background:
    Given an appropriate test context as detailed in the test data source

    @S-223
     Scenario: must successfully create allocated-magistrate case role
       Given a user with [an active IDAM profile with full permissions],
       And a user [Befta3 - who is the actor for requested role],
       And a successful call [to create org role assignments for actors & requester] as in [S-223_Org_Role_Creation],
       When a request is prepared with appropriate values,
       And the request [contains ReplaceExisting is false and reference set to caseId],
       And the request [contains allocated-magistrate role assignment],
       And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
       Then a positive response is received,
       And the response has all other details as expected,
       And a successful call [to delete case role assignment for the same actor] as in [S-223_Delete_Case_Role],
       And a successful call [to delete role assignments just created above] as in [S-223_DeleteDataForRoleAssignmentsForOrgRoles].

    @S-224
     Scenario: must successfully create hearing-judge case role
       Given a user with [an active IDAM profile with full permissions],
       And a user [Befta3 - who is the actor for requested role],
       And a successful call [to create org role assignments for actors & requester] as in [S-224_Org_Role_Creation],
       When a request is prepared with appropriate values,
       And the request [contains ReplaceExisting is false and reference set to caseId],
       And the request [contains hearing-judge case role assignment],
       And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
       Then a positive response is received,
       And the response has all other details as expected,
       And a successful call [to delete case role assignment for the same actor] as in [S-224_Delete_Case_Role],
       And a successful call [to delete role assignments just created above] as in [S-224_DeleteDataForRoleAssignmentsForOrgRoles].

    @S-225
    Scenario: must successfully create allocated-judge case role
      Given a user with [an active IDAM profile with full permissions],
      And a user [Befta3 - who is the actor for requested role],
      And a successful call [to create org role assignments for actor & requester] as in [S-225_Org_Role_Creation],
      When a request is prepared with appropriate values,
      And the request [contains ReplaceExisting is false and reference set to caseId],
      And the request [contains allocated-judge case role assignment],
      And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
      Then a positive response is received,
      And the response has all other details as expected,
      And a successful call [to delete case role assignment for the same actor] as in [S-225_Delete_Case_Role],
      And a successful call [to delete role assignments just created above] as in [S-225_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-226
  Scenario: must successfully create gatekeeping-judge case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actor & requester] as in [S-226_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains gatekeeping-judge case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-226_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-226_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-227
  Scenario: must successfully create allocated-legal-adviser case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-227_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-legal-adviser role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-227_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-227_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-228
  Scenario: must successfully create allocated-ctsc-caseworker case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-228_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-ctsc-caseworker role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-228_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-228_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-229
  Scenario: must successfully create allocated-admin-caseworker case role
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta3 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-229_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains allocated-admin-caseworker role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete case role assignment for the same actor] as in [S-229_Delete_Case_Role],
    And a successful call [to delete role assignments just created above] as in [S-229_DeleteDataForRoleAssignmentsForOrgRoles].
