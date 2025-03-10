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
class IacSystemRoleTest extends DroolBase {

    @ParameterizedTest
    @CsvSource({
        "hearing-manager,SYSTEM,IA,Asylum",
        "hearing-viewer,SYSTEM,IA,Asylum",
        "hearing-manager,SYSTEM,IA,Bail",
        "hearing-viewer,SYSTEM,IA,Bail"
    })
    void shouldApproveOrgRequestedRoleForIacHearing(String roleName, String roleCategory, String jurisdiction,
                                                    String caseType) {
        assignmentRequest.getRequest().setClientId("iac");
        assignmentRequest.getRequest().setProcess("iac-system-users");
        assignmentRequest.getRequest().setReference("iac-hearings-system-user");
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
        "hearing-manager,SYSTEM,IA",
        "hearing-viewer,SYSTEM,IA"
    })
    void shouldDeleteOrgRequestedRoleForIAcHearing(String roleName, String roleCategory, String jurisdiction) {
        assignmentRequest.getRequest().setClientId("iac");
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
    void shouldRejectIacOrgRequestedRoleForHearingFromAnotherJurisdiction(String roleName,
                                                                          String roleCategory,
                                                                          String jurisdiction,
                                                                          String caseType) {
        assignmentRequest.getRequest().setClientId("iac");
        assignmentRequest.getRequest().setProcess("iac-system-users");
        assignmentRequest.getRequest().setReference("iac-hearings-system-user");
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
        "case-allocator,SYSTEM,IA,UK"
    })
    void shouldApproveOrgRequestedRoleForIacCaseAllocator(String roleName, String roleCategory,
                                                          String jurisdiction, String primaryLocation) {
        assignmentRequest.getRequest().setClientId("iac");
        assignmentRequest.getRequest().setProcess("iac-system-users");
        assignmentRequest.getRequest().setReference("iac-case-allocator-system-user");
        assignmentRequest.getRequest().setReplaceExisting(true);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode(primaryLocation));
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
        "case-allocator,SYSTEM,IA"
    })
    void shouldDeleteOrgRequestedRoleForIacCaseAllocator(String roleName, String roleCategory, String jurisdiction) {
        assignmentRequest.getRequest().setClientId("iac");
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
        "case-allocator,SYSTEM,PRIVATELAW,UK"
    })
    void shouldRejectIacOrgRequestedRoleForCaseAllocatorFromAnotherJurisdiction(String roleName,
                                                                                String roleCategory,
                                                                                String jurisdiction,
                                                                                String primaryLocation) {
        assignmentRequest.getRequest().setClientId("iac");
        assignmentRequest.getRequest().setProcess("iac-system-users");
        assignmentRequest.getRequest().setReference("iac-case-allocator-system-user");
        assignmentRequest.getRequest().setReplaceExisting(true);
        assignmentRequest.setRequestedRoles(getRequestedOrgRole());
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.valueOf(roleCategory));
            roleAssignment.setRoleType(RoleType.ORGANISATION);
            roleAssignment.setRoleName(roleName);
            roleAssignment.setGrantType(STANDARD);
            roleAssignment.setStatus(CREATE_REQUESTED);
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
            roleAssignment.getAttributes().put("primaryLocation", convertValueJsonNode(primaryLocation));
        });

        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(Status.REJECTED, roleAssignment.getStatus());
        });
    }

}
