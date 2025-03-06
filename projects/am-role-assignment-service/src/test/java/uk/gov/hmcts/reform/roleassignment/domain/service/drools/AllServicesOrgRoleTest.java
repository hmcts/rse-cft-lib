package uk.gov.hmcts.reform.roleassignment.domain.service.drools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType.SPECIFIC;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType.STANDARD;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.APPROVED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_APPROVED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.ACTORID;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.getRequestedOrgRole;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

@RunWith(MockitoJUnitRunner.class)
class AllServicesOrgRoleTest extends DroolBase {

    @Test
    void shouldApproveOrgRequestedRoleForTCW_S001() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("tribunal-caseworker");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });

        buildExecuteKieSession();

        //assertion
        assertFalse(assignmentRequest.getRequest().isByPassOrgDroolRule());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals("tribunal-caseworker", roleAssignment.getRoleName());
            String substantive = roleAssignment.getRoleType() == RoleType.ORGANISATION ? "Y" : "N";
            assertEquals(substantive, roleAssignment.getAttributes().get("substantive").asText());
        });
    }

    @Test
    void shouldApproveOrgRequestedRoleForSTCW_S002() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("senior-tribunal-caseworker");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);

            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });

        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals("senior-tribunal-caseworker", roleAssignment.getRoleName());
            assertEquals("Y", roleAssignment.getAttributes().get("substantive").asText());
        });
    }

    //@Test
    void shouldRejectOrgRequestedRoleForTCW_WrongClientID_S003() {

        assignmentRequest.getRequest().setClientId("ccd-gw");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("tribunal-caseworker");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> assertNotEquals(Status.APPROVED,
                                                                                        roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectOrgValidationForTCW_WrongRoleCategory_S004() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.JUDICIAL);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("tribunal-caseworker");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(RoleCategory.JUDICIAL, roleAssignment.getRoleCategory());
            assertEquals("tribunal-caseworker", roleAssignment.getRoleName());
            assertEquals(Status.REJECTED, roleAssignment.getStatus());
        });
    }

    @Test
    void shouldRejectOrgValidationForTCW_WrongGrantType_S005() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("tribunal-caseworker");
            roleAssignment.setGrantType(SPECIFIC);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(SPECIFIC, roleAssignment.getGrantType());
            assertEquals(Status.REJECTED, roleAssignment.getStatus());
        });
    }

    @Test
    void shouldRejectOrgValidationForTCW_WrongClassification_S006() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setClassification(Classification.RESTRICTED);
            roleAssignment.setRoleName("tribunal-caseworker");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(Classification.RESTRICTED, roleAssignment.getClassification());
            assertEquals(Status.REJECTED, roleAssignment.getStatus());
        });
    }

    @Test
    void shouldRejectOrgValidationForSTCW_MissingJurisdiction_S007() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("senior-tribunal-caseworker");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertNull(roleAssignment.getAttributes().get("jurisdiction"));
            assertEquals(Status.REJECTED, roleAssignment.getStatus());
        });
    }

    @Test
    void shouldRejectOrgValidationForTCW_WrongJurisdiction_S008() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("tribunal-caseworker");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> assertEquals(Status.REJECTED,
                                                                                     roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectOrgValidationForTCW_MissingPrimaryLocation_S009() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("tribunal-caseworker");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertNull(roleAssignment.getAttributes().get("primaryLocation"));
            assertEquals(Status.REJECTED, roleAssignment.getStatus());
        });
    }

    @Test
    void shouldApproveDeleteRequestedRoleForOrg_S010() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(Status.DELETE_REQUESTED);
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> assertEquals(Status.DELETE_APPROVED,
                                                                                     roleAssignment.getStatus()));
    }

    @Test
    void shouldApproveDeleteRequestedRoleWithBadClientIdAndBypassDroolRule() {
        assignmentRequest.getRequest().setClientId("not_am_org_role_mapping_service");
        assignmentRequest.getRequest().setByPassOrgDroolRule(true);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(Status.DELETE_REQUESTED);
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
        });
        buildExecuteKieSession();

        //assertion
        assertTrue(assignmentRequest.getRequest().isByPassOrgDroolRule());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> assertEquals(Status.DELETE_APPROVED,
                                                                                     roleAssignment.getStatus()));
    }

    @Test
    void shouldApproveDeleteRequestedRoleWithGoodClientIdAndNoBypassDroolRule() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.getRequest().setByPassOrgDroolRule(false);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(Status.DELETE_REQUESTED);
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
        });
        buildExecuteKieSession();

        //assertion
        assertFalse(assignmentRequest.getRequest().isByPassOrgDroolRule());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> assertEquals(Status.DELETE_APPROVED,
                                                                                     roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectDeleteRequestedRoleBadClientIdAndNoBypassDroolRule() {
        assignmentRequest.getRequest().setClientId("not_am_org_role_mapping_service");
        assignmentRequest.getRequest().setByPassOrgDroolRule(false);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(DELETE_REQUESTED);
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
        });
        buildExecuteKieSession();

        //assertion
        assertFalse(assignmentRequest.getRequest().isByPassOrgDroolRule());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> assertEquals(Status.DELETE_REJECTED,
                                                                                     roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectDeleteRequestedRole_MissingRoleType_S012() {
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(Status.DELETE_REQUESTED);
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertNull(roleAssignment.getRoleType());
            assertEquals(Status.DELETE_REJECTED, roleAssignment.getStatus());
        });
    }

    @Test
    void shouldRejectDeleteRequestedRoleForOrgWrongClientId_S011() {

        assignmentRequest.getRequest().setClientId("ccd-gw");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(DELETE_REQUESTED);
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
        });
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> assertEquals(Status.DELETE_REJECTED,
                                                                                     roleAssignment.getStatus()));
    }

    @Test
    void shouldApproveOrgRequestedRoleForSTCW_withoutSubstantive() {
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("task-supervisor");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);

            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode("abc"));
        });

        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals("task-supervisor", roleAssignment.getRoleName());
            assertEquals("N", roleAssignment.getAttributes().get("substantive").asText());
        });
    }

    @Test
    void shouldApprovedRequestedRoleForOrg() {

        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequest().setClientId("not_am_org_role_mapping_service");
        assignmentRequest.getRequest().setByPassOrgDroolRule(true);
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.JUDICIAL);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setStatus(Status.CREATE_REQUESTED);
            roleAssignment.setRoleName("judge");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.getAttributes().put("region", convertValueJsonNode("north-east"));
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
        });

        //Execute Kie session
        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
                assertEquals(Status.APPROVED,roleAssignment.getStatus());
                assertEquals("Y", roleAssignment.getAttributes().get("substantive").asText());
            }
        );
    }

    @Test
    void shouldRejectOrgValidation_MissingAttributeJurisdiction() {

        assignmentRequest.setRequestedRoles(getRequestedOrgRole());

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.JUDICIAL);
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName("judge");
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(Status.CREATE_REQUESTED);
            roleAssignment.getAttributes().put("region", convertValueJsonNode("north-east"));
        });

        //Execute Kie session
        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                                   assertEquals(
                                                                       Status.REJECTED,
                                                                       roleAssignment.getStatus()
                                                                   )
        );
    }

    @ParameterizedTest
    @CsvSource({
        "hearing-manager,LEGAL_OPERATIONS,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-manager,LEGAL_OPERATIONS,STANDARD,north-east,IA,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-manager,ADMIN,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-manager,ADMIN,STANDARD,north-east,IA,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-manager,CTSC,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-manager,CTSC,STANDARD,north-east,IA,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-viewer,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-viewer,JUDICIAL,STANDARD,north-east,IA,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-viewer,LEGAL_OPERATIONS,STANDARD,north-east,IA,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-viewer,LEGAL_OPERATIONS,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-viewer,ADMIN,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-viewer,ADMIN,STANDARD,north-east,IA,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-viewer,CTSC,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-viewer,CTSC,STANDARD,north-east,IA,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-centre-admin,ADMIN,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "hearing-centre-team-leader,ADMIN,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "regional-centre-admin,ADMIN,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "regional-centre-team-leader,ADMIN,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "listed-hearing-viewer,OTHER_GOV_DEPT,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "registrar,LEGAL_OPERATIONS,STANDARD,south-east,SSCS,London,ORGANISATION,Y,Null,PUBLIC",
        "clerk,ADMIN,STANDARD,south-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "dwp,OTHER_GOV_DEPT,STANDARD,south-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "hmrc,OTHER_GOV_DEPT,STANDARD,south-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "ibca,OTHER_GOV_DEPT,STANDARD,south-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "medical,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "fee-paid-medical,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "fee-paid-disability,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "fee-paid-financial,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "case-allocator,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "case-allocator,LEGAL_OPERATIONS,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "case-allocator,ADMIN,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "case-allocator,CTSC,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "case-allocator,CTSC,STANDARD,north-east,PRIVATELAW,UK,ORGANISATION,N,Null,PUBLIC",
        "case-allocator,CTSC,STANDARD,north-east,IA,UK,ORGANISATION,N,Null,PUBLIC",
        "task-supervisor,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "task-supervisor,LEGAL_OPERATIONS,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "task-supervisor,ADMIN,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "task-supervisor,CTSC,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "task-supervisor,CTSC,STANDARD,north-east,PRIVATELAW,UK,ORGANISATION,N,Null,PUBLIC",
        "task-supervisor,CTSC,STANDARD,north-east,IA,UK,ORGANISATION,N,Null,PUBLIC",
        "hmcts-judiciary,JUDICIAL,BASIC,north-east,SSCS,UK,ORGANISATION,N,SALARIED,PRIVATE",
        "hmcts-legal-operations,LEGAL_OPERATIONS,BASIC,north-east,SSCS,UK,ORGANISATION,N,SALARIED,PRIVATE",
        "hmcts-admin,ADMIN,BASIC,north-east,SSCS,UK,ORGANISATION,N,SALARIED,PRIVATE",
        "judge,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "post-hearing-salaried-judge,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "leadership-judge,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "fee-paid-judge,JUDICIAL,STANDARD,north-east,SSCS,UK,ORGANISATION,N,Null,PUBLIC",
        "senior-tribunal-caseworker,LEGAL_OPERATIONS,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "tribunal-caseworker,LEGAL_OPERATIONS,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "hmcts-judiciary,JUDICIAL,BASIC,north-east,CIVIL,UK,ORGANISATION,N,SALARIED,PRIVATE",
        "judge,JUDICIAL,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Salaried,PUBLIC",
        "fee-paid-judge,JUDICIAL,STANDARD,north-east,CIVIL,UK,ORGANISATION,N,Fee-Paid,PUBLIC",
        "circuit-judge,JUDICIAL,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Salaried,PUBLIC",
        "leadership-judge,JUDICIAL,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Salaried,PUBLIC",
        "ctsc-team-leader,CTSC,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Null,PUBLIC",
        "ctsc-team-leader,CTSC,STANDARD,north-east,PRIVATELAW,UK,ORGANISATION,Y,Null,PUBLIC",
        "ctsc-team-leader,CTSC,STANDARD,north-east,IA,UK,ORGANISATION,Y,Null,PUBLIC",
        "senior-judge,JUDICIAL,STANDARD,north-east,IA,UK,ORGANISATION,Y,Null,PUBLIC",
        "ctsc,ADMIN,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Null,PUBLIC",
        "ctsc,CTSC,STANDARD,north-east,PRIVATELAW,UK,ORGANISATION,Y,Null,PUBLIC",
        "ctsc,CTSC,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "ctsc-team-leader,CTSC,STANDARD,north-east,SSCS,UK,ORGANISATION,Y,Null,PUBLIC",
        "nbc-team-leader,ADMIN,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Null,PUBLIC",
        "national-business-centre,ADMIN,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Null,PUBLIC",
        "hearing-centre-team-leader,ADMIN,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Null,PUBLIC",
        "hearing-centre-admin,ADMIN,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Null,PUBLIC",
        "hearing-centre-admin,ADMIN,STANDARD,north-east,PRIVATELAW,UK,ORGANISATION,Y,Null,PUBLIC",
        "tribunal-caseworker,LEGAL_OPERATIONS,STANDARD,north-east,CIVIL,UK,ORGANISATION,Y,Null,PUBLIC",
        "senior-tribunal-caseworker,LEGAL_OPERATIONS,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "tribunal-caseworker,LEGAL_OPERATIONS,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "hmcts-legal-operations,LEGAL_OPERATIONS,BASIC,north-east,ST_CIC,UK,ORGANISATION,N,Null,PRIVATE",
        "task-supervisor,LEGAL_OPERATIONS,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "case-allocator,LEGAL_OPERATIONS,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "specific-access-approver-legal-ops,LEGAL_OPERATIONS,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "hearing-centre-team-leader,ADMIN,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "hearing-centre-admin,ADMIN,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "hmcts-admin,ADMIN,BASIC,north-east,ST_CIC,UK,ORGANISATION,N,Null,PRIVATE",
        "task-supervisor,ADMIN,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "case-allocator,ADMIN,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "specific-access-approver-admin,ADMIN,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "regional-centre-team-leader,ADMIN,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "regional-centre-admin,ADMIN,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "cica,OTHER_GOV_DEPT,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "ctsc-team-leader,CTSC,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "ctsc,CTSC,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "hmcts-ctsc,CTSC,BASIC,north-east,ST_CIC,UK,ORGANISATION,N,Null,PRIVATE",
        "task-supervisor,CTSC,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "case-allocator,CTSC,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "specific-access-approver-ctsc,CTSC,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "magistrate,JUDICIAL,STANDARD,north-east,PRIVATELAW,UK,ORGANISATION,Y,Null,PUBLIC",
        "senior-judge,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "leadership-judge,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "judge,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "fee-paid-judge,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "medical,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "fee-paid-medical,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "fee-paid-tribunal-member,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "fee-paid-disability,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "fee-paid-financial,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "hmcts-judiciary,JUDICIAL,BASIC,north-east,ST_CIC,UK,ORGANISATION,N,Null,PRIVATE",
        "specific-access-approver-judiciary,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "case-allocator,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "task-supervisor,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,N,Null,PUBLIC",
        "magistrate,JUDICIAL,STANDARD,north-east,ST_CIC,UK,ORGANISATION,Y,Null,PUBLIC",
        "magistrate,JUDICIAL,STANDARD,north-east,PRIVATELAW,UK,ORGANISATION,Y,Null,PUBLIC",
        "caseworker-privatelaw-externaluser-viewonly,OTHER_GOV_DEPT,STANDARD,north-east,PRIVATELAW,UK,ORGANISATION,"
            + "N,Null,PUBLIC",
        "listed-hearing-viewer,OTHER_GOV_DEPT,STANDARD,north-east,PRIVATELAW,UK,ORGANISATION,N,Null,PUBLIC"
    })
    void shouldApproveRequestedRoleForOrg(String roleName, String roleCategory, String grantType,
                                          String region, String jurisdiction, String primaryLocation,
                                          String roleType, String expectedSubstantive, String contractType,
                                          String classification) {

        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.getRequest().setAssignerId(ACTORID);
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.valueOf(roleType));
            roleAssignment.setStatus(Status.CREATE_REQUESTED);
            roleAssignment.setClassification(Classification.valueOf(classification));
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(GrantType.valueOf(grantType));
            roleAssignment.getAttributes().put("region", convertValueJsonNode(region));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode(primaryLocation));
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
            roleAssignment.getAttributes().put("contractType", convertValueJsonNode(contractType));
        });

        //Execute Kie session
        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles()
            .forEach(roleAssignment -> {
                assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
                assertEquals(roleName, roleAssignment.getRoleName());
                assertEquals(RoleCategory.valueOf(roleCategory), roleAssignment.getRoleCategory());
                assertEquals(expectedSubstantive, roleAssignment.getAttributes().get("substantive").asText());
                assertEquals(Status.APPROVED, roleAssignment.getStatus());
            });
    }

    @ParameterizedTest
    @CsvSource({
        "hmcts-judiciary,JUDICIAL,BASIC,ORGANISATION,N,PRIVATE",
        "hmcts-legal-operations,LEGAL_OPERATIONS,BASIC,ORGANISATION,N,PRIVATE",
        "hmcts-admin,ADMIN,BASIC,ORGANISATION,N,PRIVATE"
    })
    void shouldApproveRequestedRoleForOrgHavingNoAttributes(String roleName, String roleCategory, String grantType,
                                          String roleType, String expectedSubstantive, String classification) {

        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.getRequest().setAssignerId(ACTORID);
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.valueOf(roleType));
            roleAssignment.setStatus(Status.CREATE_REQUESTED);
            roleAssignment.setClassification(Classification.valueOf(classification));
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(GrantType.valueOf(grantType));
        });

        //Execute Kie session
        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles()
            .forEach(roleAssignment -> {
                assertEquals(roleName, roleAssignment.getRoleName());
                assertEquals(RoleCategory.valueOf(roleCategory), roleAssignment.getRoleCategory());
                assertEquals(expectedSubstantive, roleAssignment.getAttributes().get("substantive").asText());
                assertEquals(Status.APPROVED, roleAssignment.getStatus());
            });
    }

    @ParameterizedTest
    @CsvSource({
        "manager,LEGAL_OPERATIONS,STANDARD,north-east,SSCS,ORGANISATION",
        "hearing-manager,JUDICIAL,STANDARD,north-east,SSCS,ORGANISATION",
        "hearing-viewer,JUDICIAL,SPECIFIC,north-east,SSCS,ORGANISATION",
        "listed-hearing-viewer,OTHER_GOV_DEPT,STANDARD,north-east,SSCS,CASE",
        "judge,JUDICIAL,BASIC,north-east,SSCS,ORGANISATION",
        "fee-paid-judge,JUDICIAL,STANDARD,north-east,CIVIL1,ORGANISATION",
        "circuit-judge,LEGAL_OPERATIONS,STANDARD,north-east,CIVIL,ORGANISATION",
        "hearing-centre-team-leader,LEGAL_OPERATIONS,STANDARD,north-east,CIVIL,ORGANISATION",
        "nbc-team-leader,ADMIN,STANDARD,north-east,SSCS,ORGANISATION",
        "national-business-centre,ADMIN,STANDARD,north-east,IA,ORGANISATION",
        "ctsc-team-leader,LEGAL_OPERATIONS,STANDARD,north-east,CIVIL,ORGANISATION",
        "magistrate,LEGAL_OPERATIONS,STANDARD,north-east,PRIVATELAW,ORGANISATION"

    })
    void shouldRejectRequestedRoleForOrg(String roleName, String roleCategory,
                                         String grantType, String region, String jurisdiction,
                                         String org) {

        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequest().setClientId("am_org_role_mapping_service");
        assignmentRequest.getRequest().setAssignerId(ACTORID);
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.valueOf(org));
            roleAssignment.setStatus(Status.CREATE_REQUESTED);
            roleAssignment.setClassification(Classification.PUBLIC);
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(GrantType.valueOf(grantType));
            roleAssignment.getAttributes().put("region", convertValueJsonNode(region));
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
            roleAssignment.getAttributes().put("substantive", convertValueJsonNode("N"));
        });

        //Execute Kie session
        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles()
            .forEach(roleAssignment -> assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "hearing-manager,SYSTEM,SSCS,Benefit",
        "hearing-viewer,SYSTEM,SSCS,Benefit"
    })
    void shouldApproveOrgRequestedRoleForHearing(String roleName, String roleCategory, String jurisdiction,
                                                 String caseType) {
        assignmentRequest.getRequest().setClientId("sscs");
        assignmentRequest.getRequest().setProcess("sscs-system-users");
        assignmentRequest.getRequest().setReference("sscs-hearings-system-user");
        assignmentRequest.getRequest().setReplaceExisting(true);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
            roleAssignment.getAttributes().put("caseType", convertValueJsonNode(caseType));
        });

        buildExecuteKieSession();

        //assertion
        assertFalse(assignmentRequest.getRequest().isByPassOrgDroolRule());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals(roleName, roleAssignment.getRoleName());
            assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "hearing-manager,SYSTEM,SSCS",
        "hearing-viewer,SYSTEM,SSCS"
    })
    void shouldDeleteOrgRequestedRoleForHearing(String roleName, String roleCategory, String jurisdiction) {
        assignmentRequest.getRequest().setClientId("sscs");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(Status.DELETE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
        });

        buildExecuteKieSession();

        //assertion
        assertFalse(assignmentRequest.getRequest().isByPassOrgDroolRule());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(DELETE_APPROVED, roleAssignment.getStatus());
            assertEquals(roleName, roleAssignment.getRoleName());
            assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
        });
    }


    @ParameterizedTest
    @CsvSource({
        "hearing-manager,SYSTEM,PRIVATELAW,PRLAPPS",
        "hearing-viewer,SYSTEM,PRIVATELAW,PRLAPPS",
        "hearing-manager,SYSTEM,PRIVATELAW,PRIVATELAW_ExceptionRecord",
        "hearing-viewer,SYSTEM,PRIVATELAW,PRIVATELAW_ExceptionRecord"
    })
    void shouldRejectSscsOrgRequestedRoleForHearingFromAnotherJurisdiction(String roleName,
                                                                           String roleCategory,
                                                                           String jurisdiction,
                                                                           String caseType) {
        assignmentRequest.getRequest().setClientId("sscs");
        assignmentRequest.getRequest().setProcess("sscs-system-users");
        assignmentRequest.getRequest().setReference("sscs-hearings-system-user");
        assignmentRequest.getRequest().setReplaceExisting(true);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
            roleAssignment.getAttributes().put("caseType", convertValueJsonNode(caseType));
        });

        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(Status.REJECTED, roleAssignment.getStatus());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "case-allocator,SYSTEM,PUBLICLAW,UK"
    })
    void shouldApprovePublicLawOrgRequestedRoleForCaseAllocator(String roleName,
                                                                String roleCategory,
                                                                String jurisdiction,
                                                                String primaryLocation) {
        assignmentRequest.getRequest().setClientId("fpl_case_service");
        assignmentRequest.getRequest().setProcess("public-law-system-users");
        assignmentRequest.getRequest().setReference("public-law-case-allocator-system-user");
        assignmentRequest.getRequest().setReplaceExisting(true);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode(primaryLocation));
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
        });

        buildExecuteKieSession();

        //assertion
        assertFalse(assignmentRequest.getRequest().isByPassOrgDroolRule());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals(roleName, roleAssignment.getRoleName());
            assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "case-allocator,SYSTEM,PUBLICLAW"
    })
    void shouldDeletePublicLawOrgRequestedRoleForCaseAllocator(String roleName,
                                                               String roleCategory,
                                                               String jurisdiction) {
        assignmentRequest.getRequest().setClientId("fpl_case_service");
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(Status.DELETE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
        });

        buildExecuteKieSession();

        //assertion
        assertFalse(assignmentRequest.getRequest().isByPassOrgDroolRule());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(DELETE_APPROVED, roleAssignment.getStatus());
            assertEquals(roleName, roleAssignment.getRoleName());
            assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "case-allocator,SYSTEM,SSCS"
    })
    void shouldRejectPublicLawOrgRequestedRoleForCaseAllocatorFromAnotherJurisdiction(String roleName,
                                                                                      String roleCategory,
                                                                                      String jurisdiction) {
        assignmentRequest.getRequest().setClientId("fpl_case_service");
        assignmentRequest.getRequest().setProcess("public-law-system-users");
        assignmentRequest.getRequest().setReference("public-law-case-allocator-system-user");
        assignmentRequest.getRequest().setReplaceExisting(true);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
        });

        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(Status.REJECTED, roleAssignment.getStatus());
        });
    }

}
