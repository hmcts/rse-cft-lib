package uk.gov.hmcts.reform.roleassignment.domain.service.drools;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.domain.model.FeatureFlag;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.APPROVED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_APPROVED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REJECTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.REJECTED;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;


class ConflictOfInterestTest extends DroolBase {

    //CREATE Self Conflict Roles
    @ParameterizedTest
    @CsvSource({
        "IA",
        "PRIVATELAW",
        "SSCS",
        "CIVIL"
    })
    void createConflictOfInterest_Role_for_allServices(String jurisdiction)  {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest().build();
        RoleAssignment assignment = TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.JUDICIAL);
        assignment.setActorId(TestDataBuilder.CASE_ALLOCATOR_ID);
        assignmentRequest.setRequestedRoles(Collections.singletonList(assignment));

        Case caseDetails = caseMap.get(jurisdiction);
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(caseDetails.getId()));
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
            roleAssignment.getAttributes().put("caseTypeId", convertValueJsonNode(caseDetails.getCaseTypeId()));
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict(jurisdiction, RoleCategory.ADMIN)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
            assertEquals(caseDetails.getId(), roleAssignment.getAttributes().get("caseId").asText());
            assertEquals(caseDetails.getCaseTypeId(), roleAssignment.getAttributes().get("caseTypeId").asText());

            assertTrue(roleAssignment.getLog()
                           .contains("Stage 1 approved : case_allocator_create_conflict_of_interest"));
            assertTrue(roleAssignment.getLog()
                           .contains("Approved : validate_role_assignment_against_patterns"));
        });
    }


    @Test
    void createSelfSubmittedConflictRole_Ia_Judicial() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest().build();
        assignmentRequest.setRequestedRoles(
                Collections.singletonList(TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.JUDICIAL)));
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
        });

        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals("IA", roleAssignment.getAttributes().get("jurisdiction").asText());
            assertEquals("Asylum", roleAssignment.getAttributes().get("caseType").asText());
            assertTrue(roleAssignment.getLog().contains("Stage 1 approved : self_create_conflict_of_interest"));
        });
    }

    @Test
    void createSelfSubmittedConflictRole_Cmc_LegalOps() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest().build();
        assignmentRequest.setRequestedRoles(Collections.singletonList(
                    TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.LEGAL_OPERATIONS)));
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode("1234567890123459"));
        });

        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals("CMC", roleAssignment.getAttributes().get("jurisdiction").asText());
            assertTrue(roleAssignment.getLog().contains("Stage 1 approved : self_create_conflict_of_interest"));
        });
    }

    //CREATE Conflict Roles

    @Test
    void createConflictRole_Ia_Judicial_with_CaseAllocator_Ia_Admin() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest().build();
        RoleAssignment assignment = TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.JUDICIAL);
        assignment.setActorId(TestDataBuilder.CASE_ALLOCATOR_ID);
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(assignment));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("IA", RoleCategory.ADMIN)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals("IA", roleAssignment.getAttributes().get("jurisdiction").asText());
            assertEquals("Asylum", roleAssignment.getAttributes().get("caseType").asText());
            assertTrue(roleAssignment.getLog()
                           .contains("Stage 1 approved : case_allocator_create_conflict_of_interest"));
            assertTrue(roleAssignment.getLog()
                           .contains("Approved : validate_role_assignment_against_patterns"));
        });
    }

    @Test
    void createConflictRole_Ia_LegalOps_with_CaseAllocator_Ia_LegalOps() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(
                    TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setRoleCategory(RoleCategory.LEGAL_OPERATIONS);
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
        });

        executeDroolRules(Collections.emptyList());

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals("IA", roleAssignment.getAttributes().get("jurisdiction").asText());
            assertEquals("Asylum", roleAssignment.getAttributes().get("caseType").asText());
            assertTrue(roleAssignment.getLog()
                           .contains("Stage 1 approved : self_create_conflict_of_interest"));
        });
    }

    @Test
    void createConflictRole_Ia_Judicial_with_CaseAllocator_Ia_LegalOps() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest().build();
        RoleAssignment assignment = TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.JUDICIAL);
        assignment.setActorId(TestDataBuilder.CASE_ALLOCATOR_ID);
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(
                    assignment));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("IA", RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(APPROVED, roleAssignment.getStatus());
            assertEquals("IA", roleAssignment.getAttributes().get("jurisdiction").asText());
            assertEquals("Asylum", roleAssignment.getAttributes().get("caseType").asText());
            assertTrue(roleAssignment.getLog()
                           .contains("Stage 1 approved : case_allocator_create_conflict_of_interest"));
            assertTrue(roleAssignment.getLog()
                           .contains("Approved : validate_role_assignment_against_patterns"));
        });
    }


    //DELETE Conflict Roles

    @Test
    void deleteConflictRole_Ia_Judicial_with_CaseAllocator_Ia_Admin() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.JUDICIAL)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(DELETE_REQUESTED);
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("IA", RoleCategory.ADMIN)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(DELETE_APPROVED, roleAssignment.getStatus());
            assertTrue(roleAssignment.getLog()
                           .contains("Case Allocator approved : case_allocator_approve_delete_case_role"));
            assertTrue(roleAssignment.getLog()
                           .contains("Stage 1 approved : case_allocator_delete_conflict_of_interest"));
        });
    }

    @Test
    void deleteConflictRole_Ia_LegalOps_with_CaseAllocator_Ia_LegalOps() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(
                    TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(DELETE_REQUESTED);
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("IA", RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(DELETE_APPROVED, roleAssignment.getStatus());
            assertTrue(roleAssignment.getLog()
                           .contains("Case Allocator approved : case_allocator_approve_delete_case_role"));
            assertTrue(roleAssignment.getLog()
                           .contains("Stage 1 approved : case_allocator_delete_conflict_of_interest"));
        });
    }

    //***********************************************Negative Scenarios**********************************************

    //Do Not Create Self Conflict

    @Test
    void doNot_createSelfSubmittedConflictRole_Ia_LegalOps_UndefinedCaseId() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(
                    TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.LEGAL_OPERATIONS)));
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(""));
        });

        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void doNot_createSelfSubmittedConflictRole_Ia_LegalOps_AuthId_ActorId_Mismatch() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(
                    TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.LEGAL_OPERATIONS)));
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
            roleAssignment.setActorId("4772dc44-268f-4d0c-8f83-f0fb662aac90");
        });

        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
            assertEquals(REJECTED, roleAssignment.getStatus()));
    }

    //Do Not Create Conflict

    @Test
    void doNot_createConflictRole_PublicClassification_with_CaseAllocator_Ia_LegalOps() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(
                    TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
            roleAssignment.setClassification(Classification.PUBLIC);
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("IA", RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void doNot_createConflictRole_SpecificGrant_with_CaseAllocator_Ia_LegalOps() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(
                    TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
            roleAssignment.setGrantType(GrantType.SPECIFIC);
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("IA", RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void doNot_createConflictRole_CaseIdUndefined_with_CaseAllocator_Ia_LegalOps() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(
                    TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(""));
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("IA", RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void doNot_createConflictRole_with_CaseAllocator_Cmc_LegalOps() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(
                    TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
            roleAssignment.setActorId("4772dc44-268f-4d0c-8f83-f0fb662aac90");
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("CMC", RoleCategory.LEGAL_OPERATIONS)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(REJECTED, roleAssignment.getStatus()));
    }

    //Do Not Delete

    @Test
    void doNot_deleteConflictRole_Ia_Judicial_with_CaseAllocator_Cmc_Admin() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.JUDICIAL)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(DELETE_REQUESTED);
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("CMC", RoleCategory.ADMIN)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(DELETE_REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void doNot_deleteConflictRole_CaseIdUndefined_with_CaseAllocator_Ia_Admin() {

        setFeatureFlags();

        assignmentRequest = TestDataBuilder.getAssignmentRequest()
            .build();
        assignmentRequest
            .setRequestedRoles(
                Collections.singletonList(TestDataBuilder.buildRoleAssignmentForConflict(RoleCategory.JUDICIAL)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            roleAssignment.setStatus(DELETE_REQUESTED);
            roleAssignment.getAttributes().clear();
            roleAssignment.getAttributes().put("caseId", convertValueJsonNode(""));
            roleAssignment.getAttributes().put("jurisdiction", convertValueJsonNode("IA"));
        });

        executeDroolRules(Collections.singletonList(
            TestDataBuilder.buildExistingRoleForConflict("CMC", RoleCategory.ADMIN)));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(DELETE_REJECTED, roleAssignment.getStatus()));
    }

    private void setFeatureFlags() {
        List<String> flags = List.of("all_wa_services_case_allocator_1_0", "sscs_case_allocator_1_0");

        for (String flag : flags) {
            featureFlags.add(
                FeatureFlag.builder().flagName(flag).status(true).build()
            );
        }
    }

}

