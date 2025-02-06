package uk.gov.hmcts.reform.roleassignment.domain.service.drools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType.STANDARD;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.APPROVED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_APPROVED;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.getRequestedOrgRole;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

@RunWith(MockitoJUnitRunner.class)
class PrivateLawSystemRoleTest extends DroolBase {

    @ParameterizedTest
    @CsvSource({
        "hearing-manager,SYSTEM,PRIVATELAW,PRLAPPS",
        "hearing-viewer,SYSTEM,PRIVATELAW,PRLAPPS",
        "hearing-manager,SYSTEM,PRIVATELAW,PRIVATELAW_ExceptionRecord",
        "hearing-viewer,SYSTEM,PRIVATELAW,PRIVATELAW_ExceptionRecord"
    })
    void shouldApprovePrivateLawOrgRequestedRoleForHearing(String roleName,
                                                           String roleCategory,
                                                           String jurisdiction,
                                                           String caseType) {
        assignmentRequest.getRequest().setClientId("fis_hmc_api");
        assignmentRequest.getRequest().setProcess("private-law-system-users");
        assignmentRequest.getRequest().setReference("private-law-hearings-system-user");
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
        "hearing-manager,SYSTEM,PRIVATELAW",
        "hearing-viewer,SYSTEM,PRIVATELAW"
    })
    void shouldDeletePrivateLawOrgRequestedRoleForHearing(String roleName,
                                                          String roleCategory,
                                                          String jurisdiction) {
        assignmentRequest.getRequest().setClientId("fis_hmc_api");
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
        "hearing-manager,SYSTEM,SSCS,Benefit",
        "hearing-viewer,SYSTEM,SSCS,Benefit"
    })
    void shouldRejectPrivateLawOrgRequestedRoleForHearingFromAnotherJurisdiction(String roleName,
                                                                                 String roleCategory,
                                                                                 String jurisdiction,
                                                                                 String caseType) {
        assignmentRequest.getRequest().setClientId("fis_hmc_api");
        assignmentRequest.getRequest().setProcess("private-law-system-users");
        assignmentRequest.getRequest().setReference("private-law-hearings-system-user");
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
        "case-allocator,SYSTEM,PRIVATELAW,PRLAPPS"
    })
    void shouldApprovePrivateLawOrgRequestedRoleForCaseAllocatorSystemUser(String roleName,
                                                           String roleCategory,
                                                           String jurisdiction,
                                                           String caseType) {

        assignmentRequest.getRequest().setClientId("prl_cos_api");
        assignmentRequest.getRequest().setProcess("private-law-system-users");
        assignmentRequest.getRequest().setReference("private-law-case-allocator-system-user");
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
        "case-allocator,SYSTEM,PRIVATELAW"
    })
    void shouldDeletePrivateLawOrgRequestedRoleForCaseAllocatorSystemUser(String roleName,
                                                          String roleCategory,
                                                          String jurisdiction) {
        assignmentRequest.getRequest().setClientId("prl_cos_api");
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
        "case-allocator,SYSTEM,SSCS,PRLAPPS"
    })
    void shouldRejectPrivateLawOrgRequestedRoleForCaseAllocatorSystemUserFromAnotherJurisdiction(String roleName,
                                                                                 String roleCategory,
                                                                                 String jurisdiction,
                                                                                 String caseType) {
        assignmentRequest.getRequest().setClientId("prl_cos_api");
        assignmentRequest.getRequest().setProcess("private-law-system-users");
        assignmentRequest.getRequest().setReference("private-law-case-allocator-system-user");
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
}
