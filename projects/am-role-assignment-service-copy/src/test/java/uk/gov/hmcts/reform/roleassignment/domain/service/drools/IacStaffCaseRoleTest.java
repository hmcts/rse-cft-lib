package uk.gov.hmcts.reform.roleassignment.domain.service.drools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.FeatureFlag;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RequestType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.util.JacksonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType.CHALLENGED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType.SPECIFIC;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.buildExistingRole;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.getRequestedCaseRole_Ia;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.getRequestedCaseRole_ra;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.getRequestedCaseRole;


@RunWith(MockitoJUnitRunner.class)
class IacStaffCaseRoleTest extends DroolBase {

    HashMap<String, JsonNode> attributes;

    public IacStaffCaseRoleTest() {
        attributes = new HashMap<>();
        attributes.put("jurisdiction", convertValueJsonNode("IA"));
        attributes.put("caseType", convertValueJsonNode("Asylum"));
    }

    @Test
    void shouldRejectCaseRequestedRole_MissingExistingRoleOfRequester_S017() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                                "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, DELETE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(
            requestedRole1.getActorId(),"senior-tribunal-caseworker",
            RoleCategory.LEGAL_OPERATIONS,
            attributes, RoleType.ORGANISATION,
            Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)
        ));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                      assertEquals(Status.DELETE_REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectRequestedRoleForDelete_WrongExistingRoleName_S019() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                                "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, DELETE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "judge",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                      assertEquals(Status.DELETE_REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectRequestedRoleForDelete_WrongExistingAttributeValue_S020() {
        RoleAssignment requestedRole1 = getRequestedCaseRole(RoleCategory.LEGAL_OPERATIONS,
                                                             "tribunal-caseworker", SPECIFIC
        );
        requestedRole1.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));
        requestedRole1.setStatus(DELETE_REQUESTED);

        List<RoleAssignment> requestedRoles = new ArrayList<>();
        requestedRoles.add(requestedRole1);
        assignmentRequest.setRequestedRoles(requestedRoles);
        setFeatureFlags();

        ExistingRoleAssignment existingActorAssignment1 = buildExistingRole(
            requestedRole1.getActorId(),
            "tribunal-caseworker",
            RoleCategory.LEGAL_OPERATIONS,
            attributes, RoleType.ORGANISATION,
            Classification.PUBLIC, GrantType.STANDARD,Status.LIVE
        );

        ExistingRoleAssignment existingRequesterAssignment1 = buildExistingRole(
            assignmentRequest.getRequest()
                .getAssignerId(),
            "senior-tribunal-caseworker",
            RoleCategory.LEGAL_OPERATIONS,
            attributes, RoleType.ORGANISATION,
            Classification.PUBLIC, GrantType.STANDARD,Status.LIVE
        );
        existingRequesterAssignment1.getAttributes().put("jurisdiction", convertValueJsonNode("CMC"));

        List<ExistingRoleAssignment> existingRoleAssignments = new ArrayList<>();
        existingRoleAssignments.add(existingActorAssignment1);
        existingRoleAssignments.add(existingRequesterAssignment1);
        // facts must contain the request
        executeDroolRules(existingRoleAssignments);

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                      assertEquals(Status.DELETE_REJECTED, roleAssignment.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "tribunal-caseworker, senior-tribunal-caseworker, judge",
        "judge, judge, senior-tribunal-caseworker",
        "tribunal-caseworker, senior-tribunal-caseworker, judge",
    })
    void shouldRejectCaseValidationForTCW_RequesterJudge_S004A(String role1, String role2, String role3) {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, role1,
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          role2, RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          role3, RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectCaseValidationForTCW_RequesterSCTW_WrongAssignerID_S005() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                             "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId() + "12",
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectCaseValidationForTCW_RequesterSCTW_WrongRoleCategory_S006() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.JUDICIAL, "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectCaseValidationForTCW_RequesterSCTW_MissingCaseID_S007() {
        RoleAssignment requestedRole1 = getRequestedCaseRole(RoleCategory.LEGAL_OPERATIONS,
                                                             "tribunal-caseworker", SPECIFIC);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectCaseValidationForTCW_RequesterSCTW_WrongCaseType_S009() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                                "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             "1234567890123457", CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
            assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectCaseValidationForTCW_RequesterSCTW_WrongAssigneeJurisdiction_S008() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                                "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));
        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
            assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectCaseValidationForTCW_WrongGrantType_S013() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                                "tribunal-caseworker",
                                                             CHALLENGED, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
            assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectCaseValidationForTCW_RequesterSCTW_MissingExistingRA_S025() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                                "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
            assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectCaseDeleteRequestedRole_WrongAssignerId_S018() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                             "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, DELETE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId() + "99",
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                      assertEquals(Status.DELETE_REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldRejectCaseRequestedRole_ForRequester_WrongRequesterJurisdiction_S021() {

        RoleAssignment requestedRole1 = getRequestedCaseRole(RoleCategory.LEGAL_OPERATIONS,"tribunal-caseworker",
                                                             SPECIFIC);
        requestedRole1.getAttributes().put("caseId", convertValueJsonNode(IA_CASE_ID));

        List<RoleAssignment> requestedRoles = new ArrayList<>();
        requestedRoles.add(requestedRole1);
        assignmentRequest.setRequestedRoles(requestedRoles);
        setFeatureFlags();

        ExistingRoleAssignment existingActorAssignment1 = buildExistingRole(
            requestedRole1.getActorId(),
            "tribunal-caseworker",
            RoleCategory.LEGAL_OPERATIONS,
            attributes, RoleType.ORGANISATION,
            Classification.PUBLIC, GrantType.STANDARD,Status.LIVE
        );

        ExistingRoleAssignment existingRequesterAssignment1 = buildExistingRole(
            assignmentRequest.getRequest().getAssignerId(), "tribunal-caseworker",
            RoleCategory.LEGAL_OPERATIONS,
            attributes, RoleType.ORGANISATION,
            Classification.PUBLIC, GrantType.STANDARD,Status.LIVE);

        existingRequesterAssignment1.getAttributes().put("jurisdiction", convertValueJsonNode("CMC"));

        List<ExistingRoleAssignment> existingRoleAssignments = new ArrayList<>();
        existingRoleAssignments.add(existingActorAssignment1);
        existingRoleAssignments.add(existingRequesterAssignment1);

        // facts must contain the request
        executeDroolRules(existingRoleAssignments);

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @Test
    void shouldApprovedCaseValidationForTCW_RequesterSTCW_Version1_1() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                             "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));
        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            assertEquals(Status.APPROVED, roleAssignment.getStatus());
            assertEquals("N", roleAssignment.getAttributes().get("substantive").asText());
        });
    }

    @Test
    void shouldDeleteApprovedRequestedRoleForCase_V1_1() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                             "tribunal-caseworker",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, DELETE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("IA"));
        existingAttributes.put("caseTypeId", convertValueJsonNode("Asylum"));

        executeDroolRules(List.of(buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "senior-tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    existingAttributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    existingAttributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));
        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                      assertEquals(Status.DELETE_APPROVED, roleAssignment.getStatus()));
    }

    @Test
    @DisplayName("Approve the case-manager, case-allocator roles with assigner is case-allocator and actioned by "
        + "parametered user")
    void shouldAcceptCaseRolesCreation_V1_1() {
        verifyCreateCaseRole_V1_1("case-manager","tribunal-caseworker", RoleCategory.LEGAL_OPERATIONS);
        verifyCreateCaseRole_V1_1("case-manager","senior-tribunal-caseworker", RoleCategory.ADMIN);
        verifyCreateCaseRole_V1_1("tribunal-caseworker","tribunal-caseworker", RoleCategory.LEGAL_OPERATIONS);
        verifyCreateCaseRole_V1_1("tribunal-caseworker","senior-tribunal-caseworker", RoleCategory.ADMIN);
        verifyCreateCaseRole_V1_1("tribunal-caseworker","senior-tribunal-caseworker", RoleCategory.LEGAL_OPERATIONS);
        verifyCreateCaseRole_V1_1("case-allocator","case-allocator", RoleCategory.LEGAL_OPERATIONS);
        verifyCreateCaseRole_V1_1("case-allocator","case-allocator", RoleCategory.ADMIN);
    }

    @DisplayName("Approve the case-manager, case-allocator roles with assigner is case-allocator and actioned by "
        + "parametered user")
    private void verifyCreateCaseRole_V1_1(String roleName, String existingRole, RoleCategory reqRoleCat) {
        //Requested role
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, roleName,
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        //Assignee holding existing role
        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          existingRole,
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  //Requesters holding case allocator org role
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "case-allocator",
                                                          reqRoleCat,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));
    }

    @Test
    @DisplayName("Approve the case-manager, case-allocator roles with assigner is case-allocator and actioned by "
        + "parametered user")
    void shouldRejectCaseRolesCreation_V1_1() {
        rejectCreateCaseRole_V1_1("tribunal-caseworker", "CMC", "standard");
        rejectCreateCaseRole_V1_1("case-allocator", "IA", "standard");
        rejectCreateCaseRole_V1_1("tribunal-caseworker","IA", "allocRoleCat");
        rejectCreateCaseRole_V1_1("tribunal-caseworker", "IA", "allocRole");
        rejectCreateCaseRole_V1_1("tribunal-caseworker", "IA", "caseType");
        rejectCreateCaseRole_V1_1("tribunal-caseworker", "IA", "privateClassification");
    }

    @DisplayName("Reject the requested roles where assigner is case-allocator")
    private void rejectCreateCaseRole_V1_1(String existingRole, String caseAllocJurisdiction, String testType) {
        String caseId;
        if (testType.equals("privateClassification")) {
            caseId = "9234567890123456";
        } else {
            caseId = IA_CASE_ID;
        }
        //Requested role
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, "case-manager",
                                                                SPECIFIC, "caseId",
                                                                caseId, CREATE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(caseId)));
        requestedRole1.setRoleType(RoleType.CASE);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        Map<String, JsonNode> attributes = new HashMap<>();
        attributes.put("jurisdiction",convertValueJsonNode(caseAllocJurisdiction));
        attributes.put("caseTypeId",convertValueJsonNode("Asylum"));

        switch (testType) {
            case "allocRoleCat":
                attributes.put("allocatedRoleCategory", convertValueJsonNode("JUDICIAL"));
                break;
            case "allocRole":
                attributes.put("allocatedRole", convertValueJsonNode("tribunal-caseworker"));
                break;
            case "caseType":
                attributes.put("caseType", convertValueJsonNode("invalidCaseType"));
                break;
        }

        List<ExistingRoleAssignment> existingRoleAssignments =

            List.of(
                //Assignee holding existing role
                buildExistingRole(requestedRole1.getActorId(),
                                        existingRole, RoleCategory.LEGAL_OPERATIONS,
                                  attributes, RoleType.ORGANISATION,
                                  Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                   //Requesters holding case allocator org role
                                   buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                           "case-allocator", RoleCategory.ADMIN,
                                                     attributes, RoleType.ORGANISATION,
                                                     Classification.PUBLIC, GrantType.STANDARD,Status.LIVE));
        existingRoleAssignments.get(1).setAttributes(attributes);

        executeDroolRules(existingRoleAssignments);

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Reject the case-manager, case-allocator roles with disabled IAC_1_1 flag")
    void shouldRejectCaseRolesCreation_disableFlag() {
        verifyCreateCaseRole_V1_0("case-manager", "tribunal-caseworker");
        verifyCreateCaseRole_V1_0("case-manager", "senior-tribunal-caseworker");
        verifyCreateCaseRole_V1_0("case-allocator", "case-allocator");
    }

    private void verifyCreateCaseRole_V1_0(String roleName, String existingRole) {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, roleName,
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setIacFlagsDisabled();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          existingRole,
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Reject creation of the case-manager role actioned by neither TCW nor STCW."
        + "expected Actioned by case-allocator")
    void shouldRejectCaseManagerRole_NoTCW_NoSTCW() {

        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, "case-manager",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Reject creation of the case-manager role with no assigner. expected assigner is case-allocator")
    void shouldRejectCaseManagerRoleCreation_NoAssigner() {

        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, "case-manager",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of());

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Reject creation of the case-allocator role actioned by TCW. expected is case-allocator")
    void shouldRejectCaseAllocatorRoleCreation_actionByTCW() {

        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,
                                                                "case-allocator",SPECIFIC,"caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Reject creation of the case-allocator role with wrong Category. expected is LEGAL_OPERATIONS")
    void shouldRejectCaseAllocatorRoleCreation_withWrongMappingFields() {

        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.ADMIN, "case-allocator",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, CREATE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Accept the delete case-manager, case-allocator role Assigner as case-allocator.")
    void shouldAcceptDeleteCaseRoles_V1_1() {
        verifyDeleteCaseRole_V1_1("case-manager", "standard");
        verifyDeleteCaseRole_V1_1("case-allocator", "standard");
        verifyDeleteCaseRole_V1_1("case-manager", "caseType");
        verifyDeleteCaseRole_V1_1("case-manager", "allocRoleCat");
        verifyDeleteCaseRole_V1_1("case-manager", "allocRole");
    }

    private void verifyDeleteCaseRole_V1_1(String roleName, String testType) {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, roleName,
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, DELETE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        List<ExistingRoleAssignment> existingRoleAssignments =
            List.of(buildExistingRole(assignmentRequest
                                                .getRequest()
                                                .getAssignerId(),
                                      "case-allocator", RoleCategory.ADMIN,
                                      attributes, RoleType.ORGANISATION,
                                      Classification.PUBLIC, GrantType.STANDARD,Status.LIVE));

        Map<String, JsonNode> attributes = new HashMap<>();
        attributes.put("jurisdiction",convertValueJsonNode("IA"));
        switch (testType) {
            case "allocRoleCat":
                attributes.put("allocatedRoleCategory", convertValueJsonNode("LEGAL_OPERATIONS"));
                break;
            case "allocRole":
                attributes.put("allocatedRole", convertValueJsonNode("case-manager"));
                break;
            case "caseType":
                attributes.put("caseType", convertValueJsonNode("Asylum"));
                break;
        }

        existingRoleAssignments.get(0).setAttributes(attributes);

        executeDroolRules(existingRoleAssignments);

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_APPROVED, ra.getStatus()));
    }

    @Test
    @DisplayName("Approve the delete case-allocator with different role Category.")
    void shouldApproveDeleteCaseAllocatorRoles_withDifferentCategory() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.ADMIN, "case-allocator",
                                                             SPECIFIC, "caseId",
                                                                IA_CASE_ID, DELETE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_APPROVED, ra.getStatus()));
    }

    @Test
    @DisplayName("Accept the delete case-manager, case-allocator role Assigner as case-allocator.")
    void shouldRejectDeleteCaseRoles_V1_1() {
        deleteCaseRole_V1_1("case-manager", "cmc");
        deleteCaseRole_V1_1("case-manager", "caseType");
        deleteCaseRole_V1_1("case-manager", "allocRoleCat");
        deleteCaseRole_V1_1("case-manager", "allocRole");
        deleteCaseRole_V1_1("case-manager", "task-supervisor");
    }

    private void deleteCaseRole_V1_1(String roleName, String testType) {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, roleName,
                                                                SPECIFIC, "caseId",
                                                                IA_CASE_ID, DELETE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        List<ExistingRoleAssignment> existingRoleAssignments =
            List.of(buildExistingRole(assignmentRequest
                                                .getRequest()
                                                .getAssignerId(), "case-allocator", RoleCategory.ADMIN,
                                      attributes, RoleType.ORGANISATION,
                                      Classification.PUBLIC, GrantType.STANDARD,Status.LIVE));

        Map<String, JsonNode> attributes = new HashMap<>();
        attributes.put("jurisdiction",convertValueJsonNode("IA"));

        switch (testType) {
            case "cmc":
                requestedRole1.getAttributes().put("jurisdiction", convertValueJsonNode("CMC"));
                break;
            case "allocRoleCat":
                attributes.put("allocatedRoleCategory", convertValueJsonNode("Judicial"));
                break;
            case "allocRole":
                attributes.put("allocatedRole", convertValueJsonNode("tribunal-caseworker"));
                break;
            case "caseType":
                requestedRole1.getAttributes().put("caseType", convertValueJsonNode("Asylum"));
                attributes.put("caseType", convertValueJsonNode("Dummy"));
                break;
            case "task-supervisor":
                existingRoleAssignments.get(0).setRoleName("task-supervisor");
                break;
        }

        existingRoleAssignments.get(0).setAttributes(attributes);

        executeDroolRules(existingRoleAssignments);

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Reject delete the case-manager, case-allocator roles with disabled IAC_1_1 flag")
    void shouldRejectDeleteCaseRoles_V1_0_disabledFlag() {
        verifyDeleteCaseRole_V1_0("case-manager", "tribunal-caseworker");
        verifyDeleteCaseRole_V1_0("case-manager", "senior-tribunal-caseworker");
        verifyDeleteCaseRole_V1_0("case-allocator","case-allocator");
    }

    private void verifyDeleteCaseRole_V1_0(String roleName, String existingRole) {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, roleName,
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, DELETE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setIacFlagsDisabled();

        executeDroolRules(List.of(buildExistingRole(requestedRole1.getActorId(),
                                                          existingRole,
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE),
                                  buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "case-allocator",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Reject deletion of the case-allocator role without existing Assigner. Expected is case-allocator")
    void shouldRejectDeleteCaseAllocatorRole_V1_1_noExistingAssigner() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS,"case-allocator",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, DELETE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of());

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Reject deletion of the case-allocator role with Assigner as TCW. Expected assigned is case-allocator")
    void shouldRejectDeleteCaseAllocatorRole_V1_1_assignerAsTCW() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.LEGAL_OPERATIONS, "case-allocator",
                                                             SPECIFIC, "caseId",
                                                             IA_CASE_ID, DELETE_REQUESTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode(IA_CASE_ID)));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(assignmentRequest.getRequest().getAssignerId(),
                                                          "tribunal-caseworker",
                                                          RoleCategory.LEGAL_OPERATIONS,
                                                    attributes, RoleType.ORGANISATION,
                                                    Classification.PUBLIC, GrantType.STANDARD,Status.LIVE)));

        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_REJECTED, ra.getStatus()));
    }

    @Test
    @DisplayName("Should Approve Deletion Of Citizen Role Based On ia_delete_creator_case_roles Drool Rule")
    void shouldApproveDeletionRequestOfCreatorRoleFromIac() {
        Map<String,JsonNode> listOfAttributes = new HashMap<>();
        listOfAttributes.put("caseId", JacksonUtils.convertValueJsonNode("12345"));
        listOfAttributes.put("caseType", JacksonUtils.convertValueJsonNode("Asylum"));
        RoleAssignment requestedRole1 = getRequestedCaseRole_Ia(RoleCategory.CITIZEN,
                                                             "[CREATOR]",
                                                             SPECIFIC,
                                                             listOfAttributes,
                                                             DELETE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("iac");
        assignmentRequest.getRequest().setRequestType(RequestType.DELETE);
        setFeatureFlags();

        buildExecuteKieSession();

        //assertion
        assignmentRequest.getRequestedRoles().forEach(
            roleAssignment -> assertEquals(Status.DELETE_APPROVED, roleAssignment.getStatus()));
    }

    static Stream<Arguments> rejectData() {
        return Stream.of(
            Arguments.of(null, "Asylum", RoleCategory.CITIZEN, "[CREATOR]", "iac", DELETE_REQUESTED),
            Arguments.of("12345", "Unknown", RoleCategory.CITIZEN, "[CREATOR]", "iac", DELETE_REQUESTED),
            Arguments.of("12345", "Asylum", RoleCategory.LEGAL_OPERATIONS, "[CREATOR]", "iac", DELETE_REQUESTED),
            Arguments.of("12345", "Asylum", RoleCategory.CITIZEN, "[APPLICANTTWO]", "iac", DELETE_REQUESTED),
            Arguments.of("12345", "Asylum", RoleCategory.CITIZEN, "[CREATOR]", "wa", DELETE_REQUESTED),
            Arguments.of("12345", "Asylum", RoleCategory.CITIZEN, "[CREATOR]", "iac", CREATE_REQUESTED)
        );
    }

    @DisplayName("Should Reject Deletion Of Citizen Role Based On ia_delete_creator_case_roles Drool Rule")
    @ParameterizedTest
    @MethodSource("rejectData")
    void shouldRejectDeletionRequestOfCreatorRoleFromIac(String caseId, String caseType, RoleCategory roleCat,
                                                         String roleName, String clientId, Status status) {
        Map<String,JsonNode> listOfAttributes = new HashMap<>();
        if (caseId == null) {
            listOfAttributes.put("caseId", null);
        } else {
            listOfAttributes.put("caseId", JacksonUtils.convertValueJsonNode(caseId));
        }
        listOfAttributes.put("caseType", JacksonUtils.convertValueJsonNode(caseType));
        RoleAssignment requestedRole1 = getRequestedCaseRole_Ia(roleCat,
                                                             roleName,
                                                             SPECIFIC,
                                                             listOfAttributes,
                                                             status);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId(clientId);
        assignmentRequest.getRequest().setRequestType(RequestType.DELETE);
        setFeatureFlags();

        buildExecuteKieSession();

        //assertion
        if (status == CREATE_REQUESTED) {
            assignmentRequest.getRequestedRoles().forEach(
                roleAssignment -> assertEquals(Status.REJECTED, roleAssignment.getStatus()));
        } else {
            assignmentRequest.getRequestedRoles().forEach(
                roleAssignment -> assertEquals(Status.DELETE_REJECTED, roleAssignment.getStatus()));
        }

    }

    private void setFeatureFlags() {
        List<String> flags = List.of("iac_1_1", "iac_jrd_1_0", "all_wa_services_case_allocator_1_0");

        for (String flag : flags) {
            featureFlags.add(
                FeatureFlag.builder().flagName(flag).status(true).build()
            );
        }
    }

    private void setIacFlagsDisabled() {
        List<String> flags = List.of("iac_1_1", "iac_jrd_1_0", "all_wa_services_case_allocator_1_0");

        for (String flag : flags) {
            if (flag.equals("all_wa_services_case_allocator_1_0")) {
                featureFlags.add(
                    FeatureFlag.builder().flagName(flag).status(true).build()
                );
            } else {
                featureFlags.add(
                    FeatureFlag.builder().flagName(flag).status(false).build()
                );
            }
        }
    }

}
