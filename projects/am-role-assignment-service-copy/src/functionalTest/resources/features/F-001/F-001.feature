@F-001
Feature: F-001 : Create Role Assignments

  Background:
    Given an appropriate test context as detailed in the test data source

  @S-001
  @Retryable(maxAttempts=3,delay=500,statusCodes={502,503,504})
  Scenario: must successfully create single Role Assignment with only mandatory fields
    Given an appropriate test context as detailed in the test data source,
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment with only mandatory fields],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-002
  Scenario: must successfully create multiple Role Assignments
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-004
  Scenario: must receive a Reject response when creation of any Role Assignment not successful
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments where one of the role has invalid data],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a negative response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-005
  Scenario: must receive a Reject response when rule validation failed
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [contains data which is not as per rule validations],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a negative response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-006
  Scenario: must receive an error response when RoleName not matched
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [contains an invalid RoleName],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a negative response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-007
  Scenario: must receive an error response when ReplaceExisting is True without Process and Reference
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [contains ReplaceExisting is true and either process or Reference value is missed],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a negative response is received,
    And the response has all other details as expected.

  @S-009
  Scenario: must receive an error response when EndTime is less than current time
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And  the request [contains EndTime is less than current time],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a negative response is received,
    And the response has all other details as expected.

  @S-010
  Scenario: must receive an error response when EndTime is less than BeginTime
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [contains EndTime is less than BeginTime],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a negative response is received,
    And the response has all other details as expected.

  @S-011
  Scenario: must successfully create single Role Assignment with RoleTypeId as organisational
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [contains RoleTypeId as organisational],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-012
  Scenario: must successfully create single Role Assignment when ReplaceExisting is True with Process and Reference
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-013
  Scenario: must successfully create multiple Role Assignments when ReplaceExisting is True with Process and Reference
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments],
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-014
  Scenario: must receive an error response when creation of any Role Assignment is not successful where ReplaceExisting is True
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And the request [contains multiple Role Assignments where one of the role has invalid data],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a negative response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-015
  Scenario: must successfully remove single Role Assignment when ReplaceExisting is True along with empty role assignment list
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And the request [contains an empty Role Assignments list],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response [contains an empty Role Assignments list],
    And the response has all other details as expected.

  @S-016
  Scenario: must successfully receive a positive response when creating same assignment record twice with ReplaceExisting set to True
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And the request [contains the same create assignment request executed above],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-017
  Scenario: must successfully receive a positive response when creating mix and match role assignments ReplaceExisting set to True
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-017_Multiple_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And the request [contains multiple Role Assignments just created and couple of new role assignments],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-018
  Scenario: must successfully receive a positive response when existing role assignments replaced with none ReplaceExisting set to True
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-018_Multiple_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And the request [contains multiple Role Assignments just created and has no new role assignments],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-019
  Scenario: must successfully receive a positive response when one of existing role assignment replaced with new
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-017_Multiple_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And the request [contains multiple Role Assignments just created and couple of new role assignments],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-091
  Scenario: must successfully store single Authorisation in new DB column Authorisations
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments with single authorisation],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-092
  Scenario: must successfully store multiple Authorisations in new DB column Authorisations
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains multiple Role Assignments with more than two authorisations],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-101
  Scenario: must successfully create Org Role Assignment without begin time and end time
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains no begin and end time for ORGANISATION role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-102
  Scenario: must successfully create Org Role Assignment with begin time and end time have null values
    Given a user with [an active IDAM profile with full permissions],
    When a request is prepared with appropriate values,
    And the request [contains begin and end time have null values for ORGANISATION role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-020
  Scenario: must retain existing records when creation of any Role Assignment is not successful where ReplaceExisting is True
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-020_Multiple_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And the request [contains multiple Role Assignments just created and couple of new role assignments],
    And the request [has invalid data for one of the new role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a negative response is received,
    And the response has all other details as expected,
    And a successful call [to get role assignments which created initially above] as in [S-020_Get_Role_Assignments_Search_Query],
    And a successful call [to get role assignments which created initially above] as in [S-020_Get_Role_Assignments_Search_Query_Second_ActorId],
    And a successful call [to delete role assignments just created above] as in [S-020_DeleteDataForRoleAssignments].

  @S-109
  Scenario: must successfully receive a positive response when creating same assignment record twice with Authorisation
    Given a user with [an active IDAM profile with full permissions],
    And a successful call [to create a role assignment for an actor] as in [S-109_CreationDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is true and have process and Reference values],
    And the request [contains authorisation field],
    And the request [contains the same create assignment request executed above],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-114
  Scenario: must successfully create single Role Assignment for CCD Case roles having role category as professional.
    Given an appropriate test context as detailed in the test data source,
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [originates from the CCD system],
    And the request [has database flag for CCD system enabled],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignmentsByServiceId].

  @S-115
  @FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
  Scenario: must reject create Role Assignment for CCD Case roles having role category as professional and invalid clientId.
    Given an appropriate test context as detailed in the test data source,
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [doesn't originate from the CCD system],
    And the request [has database flag for CCD system enabled],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a negative response is received,
    And the response has all other details as expected.

  @S-116
  Scenario: must successfully create single Role Assignment for CCD Case roles having role category as Judicial.
    Given an appropriate test context as detailed in the test data source,
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [originates from the CCD system],
    And the request [has database flag for CCD system enabled],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignmentsByServiceId].

  @S-117
  Scenario: must successfully create single Role Assignment for CCD Case roles having role category as LEGAL_OPERATIONS.
    Given an appropriate test context as detailed in the test data source,
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [originates from the CCD system],
    And the request [has database flag for CCD system enabled],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignmentsByServiceId].

  @S-118
  @FeatureToggle(RAS:ccd_bypass_1_0=on)
  Scenario: must successfully create single Role Assignment for CCD Case roles having valid role with dummy jurisdiction.
    Given an appropriate test context as detailed in the test data source,
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [originates from the CCD system],
    And the request [has database flag for CCD system enabled],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignmentsByServiceId].

  @S-119
  Scenario: must successfully create single Role Assignment for CCD Case roles having role category as CITIZEN.
    Given an appropriate test context as detailed in the test data source,
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [originates from the CCD system],
    And the request [has database flag for CCD system enabled],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignmentsByServiceId].

  @S-121
  @FeatureToggle(RAS:ccd_bypass_1_0=on)
  Scenario: must successfully create single Role Assignment for CCD Case dummy roles with dummy jurisdiction.
    Given an appropriate test context as detailed in the test data source,
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [originates from the CCD system],
    And the request [has database flag for CCD system enabled],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignmentsByServiceId],
    Then a positive response is received.

  @S-122
  Scenario: must successfully create single Role Assignment for CCD Case roles with byPassOrgDroolRule true.
    Given an appropriate test context as detailed in the test data source,
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [originates from the CCD system],
    And the request [has database flag for CCD system enabled],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignmentsByServiceId],
    Then a positive response is received.

  @S-123
  @FeatureToggle(RAS:ccd_bypass_1_0=on) @FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
  Scenario: must successfully create Org dummy roles with replace another dummy jurisdiction with ReplaceExisting set to True
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta2 - who invokes the API],
    And a user [Befta1 - who is the actor for requested role],
    And a user [Befta2 - who is the assigner],
    And a successful call [to create a role assignment for an actor] as in [S-123_CreateDataForRoleAssignment],
    When a request is prepared with appropriate values,
    And the request [contains a single Role Assignment],
    And the request [originates from the CCD system],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments].

  @S-202
  @FeatureToggle(RAS:iac_jrd_1_0=on) @FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
  Scenario: must successfully create lead-judge Case Role Assignment
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-202_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains case-allocator org role as assigner],
    And the request [contains leadership-judge org role as assignee],
    And the request [contains lead-judge case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-202_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-203
  @FeatureToggle(RAS:iac_jrd_1_0=on) @FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
  Scenario: must successfully create hearing-judge Case Role Assignment
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-203_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains case-allocator org role as assigner],
    And the request [contains senior-judge org role as assignee],
    And the request [contains hearing-judge case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-203_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-204
  @FeatureToggle(RAS:iac_jrd_1_0=on) @FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
  Scenario: must successfully create ftpa-judge Case Role Assignment
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-204_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains case-allocator org role as assigner],
    And the request [contains judge org role as assignee],
    And the request [contains ftpa-judge case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-204_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-205
  @FeatureToggle(RAS:iac_jrd_1_0=on) @FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
  Scenario: must successfully create hearing-panel-judge Case Role Assignment
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-205_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains case-allocator org role as assigner],
    And the request [contains fee-paid-judge org role as assignee],
    And the request [contains hearing-panel-judge case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-205_DeleteDataForRoleAssignmentsForOrgRoles].

  @S-206
  @FeatureToggle(RAS:iac_jrd_1_0=on) @FeatureToggle(EV:AZURE_CASE_VALIDATION_FTA_ENABLED=on)
  Scenario: must successfully create case-allocator Case Role Assignment
    Given a user with [an active IDAM profile with full permissions],
    And a user [Befta1 - who is the actor for requested role],
    And a successful call [to create org role assignments for actors & requester] as in [S-206_Org_Role_Creation],
    When a request is prepared with appropriate values,
    And the request [contains ReplaceExisting is false and reference set to caseId],
    And the request [contains case-allocator org role as assigner],
    And the request [contains case-allocator org role as assignee],
    And the request [contains case-allocator case role assignment],
    And it is submitted to call the [Create Role Assignments] operation of [Role Assignments Service],
    Then a positive response is received,
    And the response has all other details as expected,
    And a successful call [to delete role assignments just created above] as in [DeleteDataForRoleAssignments],
    And a successful call [to delete role assignments just created above] as in [S-206_DeleteDataForRoleAssignmentsForOrgRoles].
