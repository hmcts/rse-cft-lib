package uk.gov.hmcts.reform.roleassignment.domain.service.drools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.FeatureFlag;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.FeatureFlagEnum;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.ACTORID;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

@RunWith(MockitoJUnitRunner.class)
class SpecificAccessDroolsTest extends DroolBase {

    @ParameterizedTest
    @CsvSource({
        "CIVIL,specific-access-judiciary,JUDICIAL,STANDARD",
        "CIVIL,specific-access-admin,ADMIN,STANDARD",
        "CIVIL,specific-access-legal-ops,LEGAL_OPERATIONS,STANDARD",
        "CIVIL,specific-access-ctsc,CTSC,STANDARD",
        "CIVIL,specific-access-judiciary,JUDICIAL,BASIC",
        "CIVIL,specific-access-admin,ADMIN,BASIC",
        "CIVIL,specific-access-legal-ops,LEGAL_OPERATIONS,BASIC",
        "PRIVATELAW,specific-access-judiciary,JUDICIAL,STANDARD",
        "PRIVATELAW,specific-access-admin,ADMIN,STANDARD",
        "PRIVATELAW,specific-access-legal-ops,LEGAL_OPERATIONS,STANDARD",
        "PUBLICLAW,specific-access-judiciary,JUDICIAL,STANDARD",
        "PUBLICLAW,specific-access-admin,ADMIN,STANDARD",
        "PUBLICLAW,specific-access-legal-ops,LEGAL_OPERATIONS,STANDARD",
        "PUBLICLAW,specific-access-ctsc,CTSC,STANDARD",
        "EMPLOYMENT,specific-access-judiciary,JUDICIAL,STANDARD",
        "EMPLOYMENT,specific-access-legal-ops,LEGAL_OPERATIONS,STANDARD",
        "EMPLOYMENT,specific-access-admin,ADMIN,STANDARD",
        "EMPLOYMENT,specific-access-ctsc,CTSC,STANDARD",
        "SSCS,specific-access-judiciary,JUDICIAL,STANDARD",
        "SSCS,specific-access-legal-ops,LEGAL_OPERATIONS,STANDARD",
        "SSCS,specific-access-admin,ADMIN,STANDARD",
        "SSCS,specific-access-ctsc,CTSC,STANDARD",
        "ST_CIC,specific-access-judiciary,JUDICIAL,STANDARD",
        "ST_CIC,specific-access-legal-ops,LEGAL_OPERATIONS,STANDARD",
        "ST_CIC,specific-access-admin,ADMIN,STANDARD",
        "ST_CIC,specific-access-ctsc,CTSC,STANDARD"
    })
    void shouldCreate_SpecificAccessRequested(String jurisdiction, String roleName, String roleCategory,
                                                           String orgGrantType) {
        Case caseDetails = caseMap.get(jurisdiction);
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "specific-access",
            "specific-access-requested",
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.RESTRICTED,
            GrantType.BASIC,
            Status.CREATE_REQUESTED,
            "anyClient",
            true,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseTypeId", convertValueJsonNode(caseDetails.getCaseTypeId()));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          ACTORID,
                                          "judge",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.valueOf(orgGrantType),
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.APPROVED, roleAssignment.getStatus());
            Assertions.assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
            Assertions.assertEquals(caseDetails.getCaseTypeId(),
                                    roleAssignment.getAttributes().get("caseType").asText());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "CIVIL,specific-access-judiciary,JUDICIAL",
        "CIVIL,specific-access-admin,ADMIN",
        "CIVIL,specific-access-legal-ops,LEGAL_OPERATIONS",
        "CIVIL,specific-access-ctsc,CTSC",
        "PRIVATELAW,specific-access-judiciary,JUDICIAL",
        "PRIVATELAW,specific-access-admin,ADMIN",
        "PRIVATELAW,specific-access-legal-ops,LEGAL_OPERATIONS",
        "PUBLICLAW,specific-access-judiciary,JUDICIAL",
        "PUBLICLAW,specific-access-admin,ADMIN",
        "PUBLICLAW,specific-access-legal-ops,LEGAL_OPERATIONS",
        "PUBLICLAW,specific-access-ctsc,CTSC",
        "EMPLOYMENT,specific-access-judiciary,JUDICIAL",
        "EMPLOYMENT,specific-access-admin,ADMIN",
        "EMPLOYMENT,specific-access-legal-ops,LEGAL_OPERATIONS",
        "EMPLOYMENT,specific-access-ctsc,CTSC",
        "SSCS,specific-access-judiciary,JUDICIAL",
        "SSCS,specific-access-legal-ops,LEGAL_OPERATIONS",
        "SSCS,specific-access-admin,ADMIN",
        "SSCS,specific-access-ctsc,CTSC",
        "ST_CIC,specific-access-judiciary,JUDICIAL",
        "ST_CIC,specific-access-legal-ops,LEGAL_OPERATIONS",
        "ST_CIC,specific-access-admin,ADMIN",
        "ST_CIC,specific-access-ctsc,CTSC"
    })
    void shouldCreate_SpecificAccessDenied(String jurisdiction, String roleName, String roleCategory) {
        Case caseDetails = caseMap.get(jurisdiction);
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("isNew", convertValueJsonNode(false));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
                "specific-access",
                "specific-access-denied",
                RoleCategory.valueOf(roleCategory),
                RoleType.CASE,
                roleAssignmentAttributes,
                Classification.RESTRICTED,
                GrantType.BASIC,
                Status.CREATE_REQUESTED,
                "anyClient",
                true,
                "Access required for reasons",
                ACTORID,
                roleAssignmentAttributes.get("caseId").asText() + "/"
                    + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
            )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        executeDroolRules(Collections.emptyList());

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.APPROVED, roleAssignment.getStatus());
            Assertions.assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
            Assertions.assertEquals(caseDetails.getCaseTypeId(),
                                    roleAssignment.getAttributes().get("caseType").asText());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "IA,specific-access-judiciary,JUDICIAL,leadership-judge",
        "IA,specific-access-admin,ADMIN,leadership-judge",
        "IA,specific-access-ctsc,CTSC,leadership-judge",
        "IA,specific-access-legal-ops,LEGAL_OPERATIONS,leadership-judge",
        "CIVIL,specific-access-judiciary,JUDICIAL,leadership-judge",
        "CIVIL,specific-access-admin,ADMIN,nbc-team-leader",
        "CIVIL,specific-access-legal-ops,LEGAL_OPERATIONS,senior-tribunal-caseworker",
        "CIVIL,specific-access-ctsc,CTSC,ctsc-team-leader",
        "PRIVATELAW,specific-access-judiciary,JUDICIAL,specific-access-approver-judiciary",
        "PRIVATELAW,specific-access-admin,ADMIN,specific-access-approver-admin",
        "PRIVATELAW,specific-access-legal-ops,LEGAL_OPERATIONS,specific-access-approver-legal-ops",
        "PRIVATELAW,specific-access-ctsc,CTSC,specific-access-approver-ctsc",
        "PUBLICLAW,specific-access-judiciary,JUDICIAL,specific-access-approver-judiciary",
        "PUBLICLAW,specific-access-admin,ADMIN,specific-access-approver-admin",
        "PUBLICLAW,specific-access-legal-ops,LEGAL_OPERATIONS,specific-access-approver-legal-ops",
        "PUBLICLAW,specific-access-ctsc,CTSC,specific-access-approver-ctsc",
        "EMPLOYMENT,specific-access-judiciary,JUDICIAL,specific-access-approver-judiciary",
        "EMPLOYMENT,specific-access-admin,ADMIN,specific-access-approver-admin",
        "EMPLOYMENT,specific-access-legal-ops,LEGAL_OPERATIONS,specific-access-approver-legal-ops",
        "EMPLOYMENT,specific-access-ctsc,CTSC,specific-access-approver-ctsc",
        "SSCS,specific-access-judiciary,JUDICIAL,specific-access-approver-judiciary",
        "SSCS,specific-access-legal-ops,LEGAL_OPERATIONS,specific-access-approver-legal-ops",
        "SSCS,specific-access-admin,ADMIN,specific-access-approver-admin",
        "SSCS,specific-access-ctsc,CTSC,specific-access-approver-ctsc",
        "ST_CIC,specific-access-judiciary,JUDICIAL,specific-access-approver-judiciary",
        "ST_CIC,specific-access-legal-ops,LEGAL_OPERATIONS,specific-access-approver-legal-ops",
        "ST_CIC,specific-access-admin,ADMIN,specific-access-approver-admin",
        "ST_CIC,specific-access-ctsc,CTSC,specific-access-approver-ctsc"
    })
    void shouldGrantAccessFor_SpecificAccess_CaseAllocator(String caseJurisdiction, String roleName,
                                                           String roleCategory, String approver) {

        Case caseDetails = caseMap.get(caseJurisdiction);
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode(caseJurisdiction));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccessGrant(
            "specific-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.RESTRICTED,
            GrantType.SPECIFIC,
            Status.CREATE_REQUESTED,
            "xui_webapp",
            false,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        featureFlags.add(FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
                             .status(true).build());

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(caseJurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(caseDetails.getCaseTypeId()));
        existingAttributes.put("managedRoleCategory", convertValueJsonNode(roleCategory));
        existingAttributes.put("managedRole", convertValueJsonNode(roleName));
        if (!"IA".equals(caseJurisdiction)) {
            existingAttributes.put("baseLocation", convertValueJsonNode("20262"));
            existingAttributes.put("region", convertValueJsonNode("1"));
        }

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          TestDataBuilder.CASE_ALLOCATOR_ID,
                                          approver,
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.APPROVED, roleAssignment.getStatus());
            Assertions.assertEquals(caseJurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
            Assertions.assertEquals(caseDetails.getCaseTypeId(),
                                    roleAssignment.getAttributes().get("caseType").asText());
            Assertions.assertEquals(
                List.of("CCD", "ExUI", "SSIC", "RefData"),
                roleAssignment.getAuthorisations()
            );
        });
    }

    @ParameterizedTest
    @CsvSource({
        "IA,specific-access-judiciary,JUDICIAL",
        "IA,specific-access-admin,ADMIN",
        "IA,specific-access-legal-ops,LEGAL_OPERATIONS",
        "SSCS,specific-access-judiciary,JUDICIAL",
        "SSCS,specific-access-admin,ADMIN",
        "SSCS,specific-access-legal-ops,LEGAL_OPERATIONS",
        "SSCS,specific-access-ctsc,CTSC",
        "CIVIL,specific-access-judiciary,JUDICIAL",
        "CIVIL,specific-access-admin,ADMIN",
        "CIVIL,specific-access-legal-ops,LEGAL_OPERATIONS",
        "CIVIL,specific-access-ctsc,CTSC",
        "PRIVATELAW,specific-access-judiciary,JUDICIAL",
        "PRIVATELAW,specific-access-admin,ADMIN",
        "PRIVATELAW,specific-access-legal-ops,LEGAL_OPERATIONS",
        "PUBLICLAW,specific-access-judiciary,JUDICIAL",
        "PUBLICLAW,specific-access-admin,ADMIN",
        "PUBLICLAW,specific-access-legal-ops,LEGAL_OPERATIONS",
        "EMPLOYMENT,specific-access-judiciary,JUDICIAL",
        "EMPLOYMENT,specific-access-admin,ADMIN",
        "EMPLOYMENT,specific-access-legal-ops,LEGAL_OPERATIONS",
        "EMPLOYMENT,specific-access-ctsc,CTSC",
        "ST_CIC,specific-access-judiciary,JUDICIAL",
        "ST_CIC,specific-access-legal-ops,LEGAL_OPERATIONS",
        "ST_CIC,specific-access-admin,ADMIN",
        "ST_CIC,specific-access-ctsc,CTSC"
    })
    void shouldGrantAccessFor_SpecificAccessGranted_XuiClient(String jurisdiction, String roleName,
                                                              String roleCategory) {

        Case caseDetails = caseMap.get(jurisdiction);
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        roleAssignmentAttributes.put("caseTypeId", convertValueJsonNode(caseDetails.getCaseTypeId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "specific-access",
            "specific-access-granted",
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.PRIVATE,
            GrantType.BASIC,
            Status.CREATE_REQUESTED,
            "xui_webapp",
            true,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(caseDetails.getJurisdiction()));
        existingAttributes.put("caseTypeId", convertValueJsonNode(caseDetails.getCaseTypeId()));
        existingAttributes.put("requestedRole", convertValueJsonNode(roleName));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          "4772dc44-268f-4d0c-8f83-f0fb662aac84",
                                          "specific-access-requested",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.BASIC,
                                          RoleType.CASE
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.APPROVED, roleAssignment.getStatus());
            Assertions.assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
            Assertions.assertEquals(caseDetails.getCaseTypeId(),
                                    roleAssignment.getAttributes().get("caseType").asText());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "CIVIL,specific-access-judiciary,JUDICIAL",
        "CIVIL,specific-access-admin,ADMIN",
        "CIVIL,specific-access-legal-ops,LEGAL_OPERATIONS",
        "CIVIL,specific-access-ctsc,CTSC",
        "PRIVATELAW,specific-access-judiciary,JUDICIAL",
        "PRIVATELAW,specific-access-admin,ADMIN",
        "PRIVATELAW,specific-access-legal-ops,LEGAL_OPERATIONS",
        "IA,specific-access-judiciary,JUDICIAL",
        "IA,specific-access-admin,ADMIN",
        "IA,specific-access-legal-ops,LEGAL_OPERATIONS",
        "SSCS,specific-access-judiciary,JUDICIAL",
        "SSCS,specific-access-admin,ADMIN",
        "SSCS,specific-access-legal-ops,LEGAL_OPERATIONS",
        "PUBLICLAW,specific-access-judiciary,JUDICIAL",
        "PUBLICLAW,specific-access-admin,ADMIN",
        "PUBLICLAW,specific-access-legal-ops,LEGAL_OPERATIONS",
        "PUBLICLAW,specific-access-ctsc,CTSC",
        "EMPLOYMENT,specific-access-judiciary,JUDICIAL",
        "EMPLOYMENT,specific-access-admin,ADMIN",
        "EMPLOYMENT,specific-access-legal-ops,LEGAL_OPERATIONS",
        "EMPLOYMENT,specific-access-ctsc,CTSC",
        "SSCS,specific-access-judiciary,JUDICIAL",
        "SSCS,specific-access-legal-ops,LEGAL_OPERATIONS",
        "SSCS,specific-access-admin,ADMIN",
        "SSCS,specific-access-ctsc,CTSC",
        "ST_CIC,specific-access-judiciary,JUDICIAL",
        "ST_CIC,specific-access-legal-ops,LEGAL_OPERATIONS",
        "ST_CIC,specific-access-admin,ADMIN",
        "ST_CIC,specific-access-ctsc,CTSC"
    })
    void shouldRejectAccessFor_SpecificAccess_CaseAllocator_selfApproval(String jurisdiction,String roleName,
                                                                         String roleCategory) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123458"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode(jurisdiction));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
                "specific-access",
                roleName,
                RoleCategory.valueOf(roleCategory),
                RoleType.CASE,
                roleAssignmentAttributes,
                Classification.RESTRICTED,
                GrantType.SPECIFIC,
                Status.CREATE_REQUESTED,
                "anyClient",
                false,
                "Access required for reasons",
                ACTORID,
                roleAssignmentAttributes.get("caseId").asText() + "/"
                    + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
            )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(jurisdiction));
        existingAttributes.put("managedRoleCategory", convertValueJsonNode(roleCategory));
        existingAttributes.put("managedRole", convertValueJsonNode(roleName));
        existingAttributes.put("caseId", convertValueJsonNode("1234567890123458"));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          ACTORID,
                                          "case-allocator",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.REJECTED, roleAssignment.getStatus());
            Assertions.assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
            Assertions.assertEquals(jurisdiction, roleAssignment.getAttributes().get("caseType").asText());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "specific-access-denied,anyClient,JUDICIAL",
        "specific-access-denied,anyClient,ADMIN",
        "specific-access-denied,anyClient,LEGAL_OPERATIONS",
        "specific-access-denied,anyClient,CTSC",
        "specific-access-requested,xui_webapp,JUDICIAL",
        "specific-access-requested,xui_webapp,ADMIN",
        "specific-access-requested,xui_webapp,LEGAL_OPERATIONS"
    })
    void shouldDeleteAccessFor_SpecificAccess(String rolename,String clientId,String roleCategory) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode("specific-access"));
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "specific-access",
            rolename,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.PRIVATE,
            GrantType.BASIC,
            Status.DELETE_REQUESTED,
            clientId,
            false,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();

        assignmentRequest.getRequestedRoles()
            .forEach(roleAssignment -> Assertions.assertEquals(Status.DELETE_APPROVED, roleAssignment.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "specific-access-judiciary,JUDICIAL",
        "specific-access-admin,ADMIN",
        "specific-access-ctsc,CTSC",
        "specific-access-legal-ops,LEGAL_OPERATIONS"
    })
    void shouldRejectAccessFor_SpecificAccess_IncorrectFlagEnabled(String roleName, String roleCategory) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode("notAsylum"));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("notIA"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "specific-access",
            "specific-access-requested",
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.RESTRICTED,
            GrantType.BASIC,
            Status.CREATE_REQUESTED,
            "anyClient",
            false,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        ).build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_CHALLENGED_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("IA"));
        existingAttributes.put("caseTypeId", convertValueJsonNode("Asylum"));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          "4772dc44-268f-4d0c-8f83-f0fb662aac84",
                                          "case-allocator",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> Assertions.assertEquals(
            Status.REJECTED,
            roleAssignment.getStatus()
        ));
    }

    @ParameterizedTest
    @CsvSource({
        "specific-access-judiciary,JUDICIAL",
        "specific-access-admin,ADMIN",
        "specific-access-ctsc,CTSC",
        "specific-access-legal-ops,LEGAL_OPERATIONS"
    })
    void shouldRejectAccessFor_SpecificAccess_InsufficientNotes(String roleName, String roleCategory) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode("notAsylum"));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("notIA"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "specific-access",
            "specific-access-requested",
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.RESTRICTED,
            GrantType.BASIC,
            Status.CREATE_REQUESTED,
            "anyClient",
            false,
            "A",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("CIVIL"));
        existingAttributes.put("caseTypeId", convertValueJsonNode("CIVIL"));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          "4772dc44-268f-4d0c-8f83-f0fb662aac84",
                                          "judge",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                  Assertions.assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "specific-access-judge,JUDICIAL",
        "specific-access-administrator,ADMIN",
        "specific-access,LEGAL_OPERATIONS"
    })
    void shouldRejectAccessFor_Specific_IncorrectRoleName(String roleName, String roleCategory) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode("notAsylum"));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("notIA"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "specific-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.RESTRICTED,
            GrantType.SPECIFIC,
            Status.CREATE_REQUESTED,
            "anyClient",
            false,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("notIA"));
        existingAttributes.put("caseTypeId", convertValueJsonNode("notAsylum"));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          "4772dc44-268f-4d0c-8f83-f0fb662aac84",
                                          "case-allocator",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> Assertions.assertEquals(
            Status.REJECTED,
            roleAssignment.getStatus()
        ));
    }

    @ParameterizedTest
    @CsvSource({
        "specific-access-judge,JUDICIAL",
        "specific-access-administrator,ADMIN",
        "specific-access-legal-operator,LEGAL_OPERATIONS"
    })
    void shouldRejectAccessFor_SpecificAccess_NotXuiClient(String roleName, String roleCategory) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("CIVIL"));
        roleAssignmentAttributes.put("caseTypeId", convertValueJsonNode("Asylum"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "specific-access",
            "specific-access-granted",
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.PRIVATE,
            GrantType.BASIC,
            Status.CREATE_REQUESTED,
            "not_xui_webapp",
            true,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        ).build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("CIVIL"));
        existingAttributes.put("caseTypeId", convertValueJsonNode("Asylum"));
        existingAttributes.put("requestedRole", convertValueJsonNode(roleName));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          "4772dc44-268f-4d0c-8f83-f0fb662aac84",
                                          "specific-access-requested",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.CASE
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> Assertions.assertEquals(
            Status.REJECTED,
            roleAssignment.getStatus()
        ));
    }

    @ParameterizedTest
    @CsvSource({
        "specific-access-judge,JUDICIAL",
        "specific-access-administrator,ADMIN",
        "specific-access-legal-operator,LEGAL_OPERATIONS"
    })
    void shouldRejectAccessFor_SpecificAccess_CaseAllocator(String roleName, String roleCategory) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("CIVIL"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "specific-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.RESTRICTED,
            GrantType.SPECIFIC,
            Status.CREATE_REQUESTED,
            "anyClient",
            false,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        ).build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("CIVIL"));
        existingAttributes.put("caseType", convertValueJsonNode("Asylum"));
        existingAttributes.put("managedRoleCategory", convertValueJsonNode("notCorrect"));
        existingAttributes.put("managedRole", convertValueJsonNode(roleName));
        existingAttributes.put("caseId", convertValueJsonNode("1234567890123456"));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          "4772dc44-268f-4d0c-8f83-f0fb662aac84",
                                          "case-allocator",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.REJECTED, roleAssignment.getStatus());
            Assertions.assertNull(roleAssignment.getAuthorisations());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "specific-access-judiciary,JUDICIAL",
        "specific-access-admin,ADMIN",
        "specific-access-legal-ops,LEGAL_OPERATIONS"
    })
    void shouldRejectAccessFor_SpecificAccess_CaseAllocatorWithInvalidTime(String roleName,
                                                           String roleCategory) {

        Case caseDetails = caseMap.get("CIVIL");
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("CIVIL"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccessGrant(
                "specific-access",
                roleName,
                RoleCategory.valueOf(roleCategory),
                RoleType.CASE,
                roleAssignmentAttributes,
                Classification.RESTRICTED,
                GrantType.SPECIFIC,
                Status.CREATE_REQUESTED,
                "xui_webapp",
                false,
                "Access required for reasons",
                ACTORID,
                roleAssignmentAttributes.get("caseId").asText() + "/"
                    + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
            )
            .build();

        featureFlags.add(FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_SPECIFIC_1_0.getValue())
                             .status(true).build());

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("CIVIL"));
        existingAttributes.put("caseType", convertValueJsonNode(caseDetails.getCaseTypeId()));
        existingAttributes.put("managedRoleCategory", convertValueJsonNode(roleCategory));
        existingAttributes.put("managedRole", convertValueJsonNode(roleName));
        existingAttributes.put("baseLocation", convertValueJsonNode("20262"));
        existingAttributes.put("region", convertValueJsonNode("1"));
        ExistingRoleAssignment existingRoleAssignment = TestDataBuilder
            .buildExistingRoleForDrools(
                TestDataBuilder.CASE_ALLOCATOR_ID,
                "case-allocator",
                RoleCategory.valueOf(roleCategory),
                existingAttributes,
                Classification.PRIVATE,
                GrantType.STANDARD,
                RoleType.ORGANISATION
            );
        existingRoleAssignment.setEndTime(ZonedDateTime.now().minusDays(2L));
        existingRoleAssignment.setBeginTime(ZonedDateTime.now().plusDays(2L));
        executeDroolRules(List.of(existingRoleAssignment));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.REJECTED, roleAssignment.getStatus());
        });
    }
}
