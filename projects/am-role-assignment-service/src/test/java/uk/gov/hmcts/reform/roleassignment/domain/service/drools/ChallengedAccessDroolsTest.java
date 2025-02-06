package uk.gov.hmcts.reform.roleassignment.domain.service.drools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.HashMap;
import java.util.List;

import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.ACTORID;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

class ChallengedAccessDroolsTest extends DroolBase {

    @ParameterizedTest
    @CsvSource({
        "IA,challenged-access-judiciary,JUDICIAL",
        "IA,challenged-access-admin,ADMIN",
        "IA,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "IA,challenged-access-ctsc,CTSC",
        "CIVIL,challenged-access-judiciary,JUDICIAL",
        "CIVIL,challenged-access-admin,ADMIN",
        "CIVIL,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "CIVIL,challenged-access-ctsc,CTSC",
        "PRIVATELAW,challenged-access-judiciary,JUDICIAL",
        "PRIVATELAW,challenged-access-admin,ADMIN",
        "PRIVATELAW,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "PRIVATELAW,challenged-access-ctsc,CTSC",
        "PUBLICLAW,challenged-access-admin,ADMIN",
        "PUBLICLAW,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "PUBLICLAW,challenged-access-ctsc,CTSC",
        "PUBLICLAW,challenged-access-judiciary,JUDICIAL",
        "EMPLOYMENT,challenged-access-judiciary,JUDICIAL",
        "EMPLOYMENT,challenged-access-admin,ADMIN",
        "EMPLOYMENT,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "EMPLOYMENT,challenged-access-ctsc,CTSC",
        "SSCS,challenged-access-judiciary,JUDICIAL",
        "SSCS,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "SSCS,challenged-access-admin,ADMIN",
        "SSCS,challenged-access-ctsc,CTSC",
    })
    void shouldGrantAccessFor_ChallengedAccess(String jurisdiction, String roleName, String roleCategory) {
        Case caseDetails = caseMap.get(jurisdiction);
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "challenged-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.PUBLIC,
            GrantType.CHALLENGED,
            Status.CREATE_REQUESTED,
            "anyClient",
            false,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_CHALLENGED_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(caseDetails.getCaseTypeId()));
        existingAttributes.put("substantive", convertValueJsonNode("Y"));
        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          ACTORID,
                                          "judge",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.APPROVED, roleAssignment.getStatus());
            Assertions.assertEquals(caseDetails.getCaseTypeId(),
                                    roleAssignment.getAttributes().get("caseType").asText());
            Assertions.assertEquals(Classification.PUBLIC, roleAssignment.getClassification());
            Assertions.assertEquals(
                List.of("CCD", "ExUI", "SSIC", "RefData"),
                roleAssignment.getAuthorisations()
            );
        });
    }

    @ParameterizedTest
    @CsvSource({
        "IA,challenged-access-judiciary,JUDICIAL",
        "IA,challenged-access-admin,ADMIN",
        "IA,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "IA,challenged-access-ctsc,CTSC",
        "CIVIL,challenged-access-judiciary,JUDICIAL",
        "CIVIL,challenged-access-admin,ADMIN",
        "CIVIL,challenged-access-ctsc,CTSC",
        "CIVIL,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "PRIVATELAW,challenged-access-judiciary,JUDICIAL",
        "PRIVATELAW,challenged-access-admin,ADMIN",
        "PRIVATELAW,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "PRIVATELAW,challenged-access-ctsc,CTSC",
        "PUBLICLAW,challenged-access-judiciary,JUDICIAL",
        "PUBLICLAW,challenged-access-admin,ADMIN",
        "PUBLICLAW,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "PUBLICLAW,challenged-access-ctsc,CTSC",
        "EMPLOYMENT,challenged-access-judiciary,JUDICIAL",
        "EMPLOYMENT,challenged-access-admin,ADMIN",
        "EMPLOYMENT,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "EMPLOYMENT,challenged-access-ctsc,CTSC",
        "SSCS,challenged-access-judiciary,JUDICIAL",
        "SSCS,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "SSCS,challenged-access-admin,ADMIN",
        "SSCS,challenged-access-ctsc,CTSC",
    })
    void shouldGrantAccessFor_ChallengedAccess_MaxAttributes(String jurisdiction, String roleName,
                                                             String roleCategory) {
        Case caseDetails = caseMap.get(jurisdiction);
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "challenged-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.PUBLIC,
            GrantType.CHALLENGED,
            Status.CREATE_REQUESTED,
            "anyClient",
            false,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_CHALLENGED_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(caseDetails.getCaseTypeId()));
        existingAttributes.put("substantive", convertValueJsonNode("Y"));
        existingAttributes.put("baseLocation", convertValueJsonNode("1234"));
        existingAttributes.put("region", convertValueJsonNode("2"));
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

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.APPROVED, roleAssignment.getStatus());
            Assertions.assertEquals(caseDetails.getCaseTypeId(),
                                    roleAssignment.getAttributes().get("caseType").asText());
            Assertions.assertEquals(Classification.PUBLIC, roleAssignment.getClassification());
            Assertions.assertEquals(
                List.of("CCD", "ExUI", "SSIC", "RefData"),
                roleAssignment.getAuthorisations()
            );
        });
    }

    @ParameterizedTest
    @CsvSource({
        "CIVIL,challenged-access-judiciary,JUDICIAL",
        "CIVIL,challenged-access-admin,ADMIN",
        "CIVIL,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "CIVIL,challenged-access-ctsc,CTSC",
        "PRIVATELAW,challenged-access-judiciary,JUDICIAL",
        "PRIVATELAW,challenged-access-admin,ADMIN",
        "PRIVATELAW,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "PUBLICLAW,challenged-access-judiciary,JUDICIAL",
        "PUBLICLAW,challenged-access-admin,ADMIN",
        "PUBLICLAW,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "PUBLICLAW,challenged-access-ctsc,CTSC",
        "EMPLOYMENT,challenged-access-judiciary,JUDICIAL",
        "EMPLOYMENT,challenged-access-admin,ADMIN",
        "EMPLOYMENT,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "EMPLOYMENT,challenged-access-ctsc,CTSC",
        "SSCS,challenged-access-judiciary,JUDICIAL",
        "SSCS,challenged-access-legal-ops,LEGAL_OPERATIONS",
        "SSCS,challenged-access-admin,ADMIN",
        "SSCS,challenged-access-ctsc,CTSC",
    })
    void shouldRejectAccessFor_ChallengedAccess_MaxAttributes_sameRegion(String jurisdiction, String roleName,
                                                             String roleCategory) {
        Case caseDetails = caseMap.get(jurisdiction);
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
                "challenged-access",
                roleName,
                RoleCategory.valueOf(roleCategory),
                RoleType.CASE,
                roleAssignmentAttributes,
                Classification.PUBLIC,
                GrantType.CHALLENGED,
                Status.CREATE_REQUESTED,
                "anyClient",
                false,
                "Access required for reasons",
                ACTORID,
                roleAssignmentAttributes.get("caseId").asText() + "/"
                    + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
            )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_CHALLENGED_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(caseDetails.getCaseTypeId()));
        existingAttributes.put("substantive", convertValueJsonNode("Y"));
        existingAttributes.put("baseLocation", convertValueJsonNode("20262"));
        existingAttributes.put("region", convertValueJsonNode("1"));
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
        "challenged-access-legal-ops,JUDICIAL",
        "challenged-access-judiciary,ADMIN",
        "challenged-access-ctsc,CTSC",
        "challenged-access-admin,LEGAL_OPERATIONS",
    })
    void shouldRejectAccessFor_ChallengedAccess_IncorrectRoleName(String roleName, String roleCategory) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode("notAsylum"));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("IA"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "challenged-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.PUBLIC,
            GrantType.CHALLENGED,
            Status.CREATE_REQUESTED,
            "anyClient",
            false,
            "Access required for reasons",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_CHALLENGED_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("IA"));
        existingAttributes.put("caseType", convertValueJsonNode("Asylum"));
        existingAttributes.put("substantive", convertValueJsonNode("Y"));
        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          ACTORID,
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
        "challenged-access-judiciary,JUDICIAL",
        "challenged-access-admin,ADMIN",
        "challenged-access-ctsc,CTSC",
        "challenged-access-legal-ops,LEGAL_OPERATIONS",
    })
    void shouldRejectAccessFor_ChallengedAccess_IncorrectFlagEnabled(String roleName,
                                                                     String roleCategory) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode("notAsylum"));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("IA"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "challenged-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.PUBLIC,
            GrantType.CHALLENGED,
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
        existingAttributes.put("jurisdiction", convertValueJsonNode("IA"));
        existingAttributes.put("caseType", convertValueJsonNode("Asylum"));
        existingAttributes.put("substantive", convertValueJsonNode("Y"));
        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          ACTORID,
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
        "challenged-access-judiciary,JUDICIAL",
        "challenged-access-admin,ADMIN",
        "challenged-access-ctsc,CTSC",
        "challenged-access-legal-ops,LEGAL_OPERATIONS",
    })
    void shouldRejectAccessFor_ChallengedAccess_InsufficientNotes(String roleName, String roleCategory) {
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode("notAsylum"));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("IA"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "challenged-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.PUBLIC,
            GrantType.CHALLENGED,
            Status.CREATE_REQUESTED,
            "anyClient",
            false,
            "A",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_CHALLENGED_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("IA"));
        existingAttributes.put("caseType", convertValueJsonNode("Asylum"));
        existingAttributes.put("substantive", convertValueJsonNode("Y"));
        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          ACTORID,
                                          "judge",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles()
            .forEach(roleAssignment -> Assertions.assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "challenged-access-judiciary,JUDICIAL",
        "challenged-access-admin,ADMIN",
        "challenged-access-ctsc,CTSC",
        "challenged-access-legal-ops,LEGAL_OPERATIONS",
    })
    void shouldRejectAccessFor_ChallengedAccess_PastEndTime(String roleName, String roleCategory) {
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode("notAsylum"));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("IA"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
            "challenged-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.PUBLIC,
            GrantType.CHALLENGED,
            Status.CREATE_REQUESTED,
            "anyClient",
            false,
            "A",
            ACTORID,
            roleAssignmentAttributes.get("caseId").asText() + "/"
                + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
        )
            .build();

        //Update the assignment record to have past End time
        assignmentRequest.getRequestedRoles().stream().findFirst()
            .get().setEndTime(ZonedDateTime.now().minusHours(1L));

        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_CHALLENGED_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("IA"));
        existingAttributes.put("caseType", convertValueJsonNode("Asylum"));
        existingAttributes.put("substantive", convertValueJsonNode("Y"));
        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          ACTORID,
                                          "judge",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles()
            .forEach(roleAssignment -> Assertions.assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "challenged-access-judiciary,JUDICIAL",
        "challenged-access-admin,ADMIN",
        "challenged-access-ctsc,CTSC",
        "challenged-access-legal-ops,LEGAL_OPERATIONS",
    })
    void shouldRejectAccessFor_ChallengedAccess_ExistingPastEndTime(String roleName, String roleCategory) {
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1234567890123456"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode("notAsylum"));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode("IA"));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
                "challenged-access",
                roleName,
                RoleCategory.valueOf(roleCategory),
                RoleType.CASE,
                roleAssignmentAttributes,
                Classification.PUBLIC,
                GrantType.CHALLENGED,
                Status.CREATE_REQUESTED,
                "anyClient",
                false,
                "A",
                ACTORID,
                roleAssignmentAttributes.get("caseId").asText() + "/"
                    + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
            )
            .build();


        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.IAC_CHALLENGED_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("IA"));
        existingAttributes.put("caseType", convertValueJsonNode("Asylum"));
        existingAttributes.put("substantive", convertValueJsonNode("Y"));
        ExistingRoleAssignment existingRoleAssignment = TestDataBuilder
            .buildExistingRoleForDrools(
                ACTORID,
                "judge",
                RoleCategory.valueOf(roleCategory),
                existingAttributes,
                Classification.PRIVATE,
                GrantType.STANDARD,
                RoleType.ORGANISATION
            );
        existingRoleAssignment.setEndTime(ZonedDateTime.now().minusDays(2L));
        existingRoleAssignment.setBeginTime(ZonedDateTime.now().plusDays(2L));
        executeDroolRules(List.of(existingRoleAssignment));

        assignmentRequest.getRequestedRoles()
            .forEach(roleAssignment -> Assertions.assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "SSCS,challenged-access-judiciary,JUDICIAL",
    })
    void shouldGrantAccessFor_ChallengedAccess_SSCS_FeePaid(String jurisdiction, String roleName, String roleCategory) {
        Case caseDetails = caseMap.get(jurisdiction);
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
                "challenged-access",
                roleName,
                RoleCategory.valueOf(roleCategory),
                RoleType.CASE,
                roleAssignmentAttributes,
                Classification.PUBLIC,
                GrantType.CHALLENGED,
                Status.CREATE_REQUESTED,
                "anyClient",
                false,
                "Access required for reasons",
                ACTORID,
                roleAssignmentAttributes.get("caseId").asText() + "/"
                    + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
            )
            .build();


        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.SSCS_CHALLENGED_1_0.getValue())
            .status(true).build();

        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(caseDetails.getCaseTypeId()));
        existingAttributes.put("substantive", convertValueJsonNode("N"));
        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          ACTORID,
                                          "fee-paid-judge",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            Assertions.assertEquals(Status.APPROVED, roleAssignment.getStatus());
            Assertions.assertEquals(caseDetails.getCaseTypeId(),
                                    roleAssignment.getAttributes().get("caseType").asText());
            Assertions.assertEquals(Classification.PUBLIC, roleAssignment.getClassification());
            Assertions.assertEquals(
                List.of("CCD", "ExUI", "SSIC", "RefData"),
                roleAssignment.getAuthorisations()
            );
        });
    }

    @ParameterizedTest
    @CsvSource({
        "IA,challenged-access-judiciary,JUDICIAL,fee-paid-judge",//wrong jurisdiction
        "SSCS,challenged-access-admin,ADMIN,fee-paid-judge",//wrong role name and category
        "SSCS,challenged-access-ctsc,CTSC,fee-paid-judge",//wrong role name and category
        "SSCS,challenged-access-legal-ops,LEGAL_OPERATIONS,fee-paid-judge",//wrong role name and category
        "SSCS,challenged-access-judiciary,JUDICIAL,judge",//wrong existing role name
    })
    void shouldRejectAccessFor_ChallengedAccess_SSCS_FeePaid(String jurisdiction, String roleName,
                                                             String roleCategory,String existingRoleName) {
        Case caseDetails = caseMap.get(jurisdiction);
        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(caseDetails.getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccess(
                "challenged-access",
                roleName,
                RoleCategory.valueOf(roleCategory),
                RoleType.CASE,
                roleAssignmentAttributes,
                Classification.PUBLIC,
                GrantType.CHALLENGED,
                Status.CREATE_REQUESTED,
                "anyClient",
                false,
                "Access required for reasons",
                ACTORID,
                roleAssignmentAttributes.get("caseId").asText() + "/"
                    + roleAssignmentAttributes.get("requestedRole").asText() + "/" + ACTORID
            )
            .build();


        FeatureFlag featureFlag = FeatureFlag.builder().flagName(FeatureFlagEnum.SSCS_CHALLENGED_1_0.getValue())
            .status(true).build();

        featureFlags.add(featureFlag);

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(caseDetails.getCaseTypeId()));
        existingAttributes.put("substantive", convertValueJsonNode("N"));
        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          ACTORID,
                                          existingRoleName,
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          Classification.PRIVATE,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )));

        assignmentRequest.getRequestedRoles().forEach(
            roleAssignment -> Assertions.assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }
}
