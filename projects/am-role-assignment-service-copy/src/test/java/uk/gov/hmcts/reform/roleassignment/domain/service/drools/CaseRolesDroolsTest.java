package uk.gov.hmcts.reform.roleassignment.domain.service.drools;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.hmcts.reform.roleassignment.domain.model.FeatureFlag;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification.PUBLIC;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType.SPECIFIC;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.APPROVED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.REJECTED;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.CASE_ALLOCATOR_ID;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.buildExistingRole;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

class CaseRolesDroolsTest extends DroolBase {


    @ParameterizedTest
    @CsvSource({
        // SSCS Benefit
        "SSCS,Benefit,hearing-judge,JUDICIAL,RESTRICTED,judge,Y",
        "SSCS,Benefit,hearing-judge,JUDICIAL,RESTRICTED,fee-paid-judge,Y",
        "SSCS,Benefit,tribunal-member-1,JUDICIAL,RESTRICTED,medical,Y",
        "SSCS,Benefit,tribunal-member-1,JUDICIAL,RESTRICTED,fee-paid-medical,Y",
        "SSCS,Benefit,tribunal-member-1,JUDICIAL,RESTRICTED,fee-paid-disability,Y",
        "SSCS,Benefit,tribunal-member-1,JUDICIAL,RESTRICTED,fee-paid-financial,Y",
        "SSCS,Benefit,tribunal-member-1,JUDICIAL,RESTRICTED,fee-paid-tribunal-member,Y",
        "SSCS,Benefit,tribunal-member-2,JUDICIAL,RESTRICTED,medical,Y",
        "SSCS,Benefit,tribunal-member-2,JUDICIAL,RESTRICTED,fee-paid-medical,Y",
        "SSCS,Benefit,tribunal-member-2,JUDICIAL,RESTRICTED,fee-paid-disability,Y",
        "SSCS,Benefit,tribunal-member-2,JUDICIAL,RESTRICTED,fee-paid-financial,Y",
        "SSCS,Benefit,tribunal-member-2,JUDICIAL,RESTRICTED,fee-paid-tribunal-member,Y",
        "SSCS,Benefit,tribunal-member-3,JUDICIAL,RESTRICTED,medical,Y",
        "SSCS,Benefit,tribunal-member-3,JUDICIAL,RESTRICTED,fee-paid-medical,Y",
        "SSCS,Benefit,tribunal-member-3,JUDICIAL,RESTRICTED,fee-paid-disability,Y",
        "SSCS,Benefit,tribunal-member-3,JUDICIAL,RESTRICTED,fee-paid-financial,Y",
        "SSCS,Benefit,tribunal-member-3,JUDICIAL,RESTRICTED,fee-paid-tribunal-member,Y",
        "SSCS,Benefit,appraiser-1,JUDICIAL,RESTRICTED,judge,Y",
        "SSCS,Benefit,appraiser-1,JUDICIAL,RESTRICTED,fee-paid-judge,Y",
        "SSCS,Benefit,appraiser-1,JUDICIAL,RESTRICTED,fee-paid-medical,Y",
        "SSCS,Benefit,appraiser-1,JUDICIAL,RESTRICTED,medical,Y",
        "SSCS,Benefit,appraiser-1,JUDICIAL,RESTRICTED,fee-paid-disability,Y",
        "SSCS,Benefit,appraiser-1,JUDICIAL,RESTRICTED,fee-paid-financial,Y",
        "SSCS,Benefit,appraiser-2,JUDICIAL,RESTRICTED,medical,Y",
        "SSCS,Benefit,appraiser-2,JUDICIAL,RESTRICTED,fee-paid-medical,Y",
        "SSCS,Benefit,appraiser-2,JUDICIAL,RESTRICTED,judge,Y",
        "SSCS,Benefit,appraiser-2,JUDICIAL,RESTRICTED,fee-paid-judge,Y",
        "SSCS,Benefit,appraiser-2,JUDICIAL,RESTRICTED,fee-paid-disability,Y",
        "SSCS,Benefit,appraiser-2,JUDICIAL,RESTRICTED,fee-paid-financial,Y",
        "SSCS,Benefit,interloc-judge,JUDICIAL,RESTRICTED,judge,Y",
        "SSCS,Benefit,interloc-judge,JUDICIAL,RESTRICTED,fee-paid-judge,Y",
        "SSCS,Benefit,post-hearing-judge,JUDICIAL,RESTRICTED,judge,Y",
        "SSCS,Benefit,case-allocator,JUDICIAL,RESTRICTED,case-allocator,N",
        "SSCS,Benefit,case-allocator,LEGAL_OPERATIONS,RESTRICTED,case-allocator,N",
        "SSCS,Benefit,registrar,LEGAL_OPERATIONS,RESTRICTED,registrar,Y",
        "SSCS,Benefit,allocated-tribunal-caseworker,LEGAL_OPERATIONS,RESTRICTED,tribunal-caseworker,Y",
        "SSCS,Benefit,allocated-admin-caseworker,ADMIN,RESTRICTED,hearing-centre-admin,Y",
        "SSCS,Benefit,allocated-admin-caseworker,ADMIN,RESTRICTED,regional-centre-admin,Y",
        "SSCS,Benefit,allocated-admin-caseworker,ADMIN,RESTRICTED,clerk,Y",
        "SSCS,Benefit,allocated-ctsc-caseworker,CTSC,RESTRICTED,ctsc,Y",
        // PRIVATELAW PRLAPPS
        "PRIVATELAW,PRLAPPS,hearing-judge,JUDICIAL,RESTRICTED,judge,",
        "PRIVATELAW,PRLAPPS,gatekeeping-judge,JUDICIAL,RESTRICTED,judge,",
        "PRIVATELAW,PRLAPPS,allocated-judge,JUDICIAL,RESTRICTED,judge,",
        "PRIVATELAW,PRLAPPS,allocated-magistrate,JUDICIAL,RESTRICTED,magistrate,",
        "PRIVATELAW,PRLAPPS,allocated-legal-adviser,LEGAL_OPERATIONS,PUBLIC,tribunal-caseworker,Y",
        "PRIVATELAW,PRLAPPS,allocated-legal-adviser,LEGAL_OPERATIONS,RESTRICTED,tribunal-caseworker,Y",
        "PRIVATELAW,PRLAPPS,allocated-ctsc-caseworker,CTSC,RESTRICTED,ctsc,Y",
        "PRIVATELAW,PRLAPPS,allocated-ctsc-caseworker,CTSC,RESTRICTED,ctsc-team-leader,Y",
        "PRIVATELAW,PRLAPPS,allocated-admin-caseworker,ADMIN,RESTRICTED,hearing-centre-admin,Y",
        "PRIVATELAW,PRLAPPS,allocated-admin-caseworker,ADMIN,RESTRICTED,hearing-centre-team-leader,Y",
        // PUBLICLAW CARE_SUPERVISION_EPO
        "PUBLICLAW,CARE_SUPERVISION_EPO,hearing-judge,JUDICIAL,RESTRICTED,judge,Y",
        "PUBLICLAW,CARE_SUPERVISION_EPO,hearing-judge,JUDICIAL,RESTRICTED,fee-paid-judge,Y",
        "PUBLICLAW,CARE_SUPERVISION_EPO,allocated-magistrate,JUDICIAL,RESTRICTED,magistrate,Y",
        "PUBLICLAW,CARE_SUPERVISION_EPO,allocated-judge,JUDICIAL,RESTRICTED,judge,Y",
        "PUBLICLAW,CARE_SUPERVISION_EPO,allocated-judge,JUDICIAL,RESTRICTED,fee-paid-judge,Y",
        "PUBLICLAW,CARE_SUPERVISION_EPO,allocated-legal-adviser,LEGAL_OPERATIONS,PUBLIC,tribunal-caseworker,Y",
        "PUBLICLAW,CARE_SUPERVISION_EPO,hearing-legal-adviser,LEGAL_OPERATIONS,PUBLIC,tribunal-caseworker,Y",
        "PUBLICLAW,CARE_SUPERVISION_EPO,allocated-legal-adviser,LEGAL_OPERATIONS,RESTRICTED,tribunal-caseworker,Y",
        "PUBLICLAW,CARE_SUPERVISION_EPO,hearing-legal-adviser,LEGAL_OPERATIONS,RESTRICTED,tribunal-caseworker,Y",
        // EMPLOYMENT ET_EnglandWales
        "EMPLOYMENT,ET_EnglandWales,lead-judge,JUDICIAL,PUBLIC,leadership-judge,Y",
        "EMPLOYMENT,ET_EnglandWales,lead-judge,JUDICIAL,PUBLIC,judge,Y",
        "EMPLOYMENT,ET_EnglandWales,hearing-judge,JUDICIAL,PUBLIC,leadership-judge,Y",
        "EMPLOYMENT,ET_EnglandWales,hearing-judge,JUDICIAL,PUBLIC,judge,Y",
        "EMPLOYMENT,ET_EnglandWales,hearing-judge,JUDICIAL,PUBLIC,fee-paid-judge,Y",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-1,JUDICIAL,PUBLIC,tribunal-member,Y",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-2,JUDICIAL,PUBLIC,tribunal-member,Y",
        "EMPLOYMENT,ET_EnglandWales,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC,tribunal-caseworker,Y",
        "EMPLOYMENT,ET_EnglandWales,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC,senior-tribunal-caseworker,Y",
        "EMPLOYMENT,ET_EnglandWales,allocated-admin-caseworker,ADMIN,PUBLIC,hearing-centre-admin,Y",
        "EMPLOYMENT,ET_EnglandWales,allocated-admin-caseworker,ADMIN,PUBLIC,hearing-centre-team-leader,Y",
        "EMPLOYMENT,ET_EnglandWales,allocated-admin-caseworker,ADMIN,PUBLIC,regional-centre-admin,Y",
        "EMPLOYMENT,ET_EnglandWales,allocated-admin-caseworker,ADMIN,PUBLIC,regional-centre-team-leader,Y",
        "EMPLOYMENT,ET_EnglandWales,allocated-admin-caseworker,ADMIN,PUBLIC,clerk,Y",
        "EMPLOYMENT,ET_EnglandWales,allocated-ctsc-caseworker,CTSC,PUBLIC,ctsc,Y",
        "EMPLOYMENT,ET_EnglandWales,allocated-ctsc-caseworker,CTSC,PUBLIC,ctsc-team-leader,Y",
        // EMPLOYMENT ET_EnglandWales_Multiple
        "EMPLOYMENT,ET_EnglandWales_Multiple,lead-judge,JUDICIAL,PUBLIC,leadership-judge,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,lead-judge,JUDICIAL,PUBLIC,judge,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,hearing-judge,JUDICIAL,PUBLIC,leadership-judge,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,hearing-judge,JUDICIAL,PUBLIC,judge,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,hearing-judge,JUDICIAL,PUBLIC,fee-paid-judge,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-1,JUDICIAL,PUBLIC,tribunal-member,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-2,JUDICIAL,PUBLIC,tribunal-member,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC,"
            + "tribunal-caseworker,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC,"
            + "senior-tribunal-caseworker,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,hearing-centre-admin,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,hearing-centre-team-leader,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,regional-centre-admin,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,regional-centre-team-leader,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,clerk,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-ctsc-caseworker,CTSC,PUBLIC,ctsc,Y",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-ctsc-caseworker,CTSC,PUBLIC,ctsc-team-leader,Y",
        // EMPLOYMENT ET_Scotland
        "EMPLOYMENT,ET_Scotland,lead-judge,JUDICIAL,PUBLIC,leadership-judge,Y",
        "EMPLOYMENT,ET_Scotland,lead-judge,JUDICIAL,PUBLIC,judge,Y",
        "EMPLOYMENT,ET_Scotland,hearing-judge,JUDICIAL,PUBLIC,leadership-judge,Y",
        "EMPLOYMENT,ET_Scotland,hearing-judge,JUDICIAL,PUBLIC,judge,Y",
        "EMPLOYMENT,ET_Scotland,hearing-judge,JUDICIAL,PUBLIC,fee-paid-judge,Y",
        "EMPLOYMENT,ET_Scotland,tribunal-member-1,JUDICIAL,PUBLIC,tribunal-member,Y",
        "EMPLOYMENT,ET_Scotland,tribunal-member-2,JUDICIAL,PUBLIC,tribunal-member,Y",
        "EMPLOYMENT,ET_Scotland,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC,tribunal-caseworker,Y",
        "EMPLOYMENT,ET_Scotland,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC,senior-tribunal-caseworker,Y",
        "EMPLOYMENT,ET_Scotland,allocated-admin-caseworker,ADMIN,PUBLIC,hearing-centre-admin,Y",
        "EMPLOYMENT,ET_Scotland,allocated-admin-caseworker,ADMIN,PUBLIC,hearing-centre-team-leader,Y",
        "EMPLOYMENT,ET_Scotland,allocated-admin-caseworker,ADMIN,PUBLIC,regional-centre-admin,Y",
        "EMPLOYMENT,ET_Scotland,allocated-admin-caseworker,ADMIN,PUBLIC,regional-centre-team-leader,Y",
        "EMPLOYMENT,ET_Scotland,allocated-admin-caseworker,ADMIN,PUBLIC,clerk,Y",
        "EMPLOYMENT,ET_Scotland,allocated-ctsc-caseworker,CTSC,PUBLIC,ctsc,Y",
        "EMPLOYMENT,ET_Scotland,allocated-ctsc-caseworker,CTSC,PUBLIC,ctsc-team-leader,Y",
        // EMPLOYMENT ET_Scotland_Multiple
        "EMPLOYMENT,ET_Scotland_Multiple,lead-judge,JUDICIAL,PUBLIC,leadership-judge,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,lead-judge,JUDICIAL,PUBLIC,judge,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,hearing-judge,JUDICIAL,PUBLIC,leadership-judge,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,hearing-judge,JUDICIAL,PUBLIC,judge,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,hearing-judge,JUDICIAL,PUBLIC,fee-paid-judge,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-1,JUDICIAL,PUBLIC,tribunal-member,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-2,JUDICIAL,PUBLIC,tribunal-member,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC,tribunal-caseworker,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC,"
            + "senior-tribunal-caseworker,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,hearing-centre-admin,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,hearing-centre-team-leader,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,regional-centre-admin,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,regional-centre-team-leader,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC,clerk,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-ctsc-caseworker,CTSC,PUBLIC,ctsc,Y",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-ctsc-caseworker,CTSC,PUBLIC,ctsc-team-leader,Y",
        // ST_CIC CriminalInjuriesCompensation
        "ST_CIC,CriminalInjuriesCompensation,hearing-judge,JUDICIAL,PUBLIC,judge,Y",
        "ST_CIC,CriminalInjuriesCompensation,hearing-judge,JUDICIAL,PUBLIC,fee-paid-judge,Y",
        "ST_CIC,CriminalInjuriesCompensation,interloc-judge,JUDICIAL,PUBLIC,judge,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-1,JUDICIAL,PUBLIC,medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-1,JUDICIAL,PUBLIC,fee-paid-medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-1,JUDICIAL,PUBLIC,fee-paid-tribunal-member,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-1,JUDICIAL,PUBLIC,fee-paid-disability,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-1,JUDICIAL,PUBLIC,fee-paid-financial,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-2,JUDICIAL,PUBLIC,medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-2,JUDICIAL,PUBLIC,fee-paid-medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-2,JUDICIAL,PUBLIC,fee-paid-tribunal-member,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-2,JUDICIAL,PUBLIC,fee-paid-disability,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-2,JUDICIAL,PUBLIC,fee-paid-financial,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-3,JUDICIAL,PUBLIC,medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-3,JUDICIAL,PUBLIC,fee-paid-medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-3,JUDICIAL,PUBLIC,fee-paid-tribunal-member,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-3,JUDICIAL,PUBLIC,fee-paid-disability,Y",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-3,JUDICIAL,PUBLIC,fee-paid-financial,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1,JUDICIAL,PUBLIC,judge,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1,JUDICIAL,PUBLIC,medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1,JUDICIAL,PUBLIC,fee-paid-medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1,JUDICIAL,PUBLIC,fee-paid-tribunal-member,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1,JUDICIAL,PUBLIC,fee-paid-disability,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1,JUDICIAL,PUBLIC,fee-paid-financial,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2,JUDICIAL,PUBLIC,judge,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2,JUDICIAL,PUBLIC,medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2,JUDICIAL,PUBLIC,fee-paid-medical,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2,JUDICIAL,PUBLIC,fee-paid-tribunal-member,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2,JUDICIAL,PUBLIC,fee-paid-disability,Y",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2,JUDICIAL,PUBLIC,fee-paid-financial,Y",
        "ST_CIC,CriminalInjuriesCompensation,allocated-legal-officer,LEGAL_OPERATIONS,RESTRICTED,tribunal-caseworker,Y",
        "ST_CIC,CriminalInjuriesCompensation,allocated-legal-officer,LEGAL_OPERATIONS,RESTRICTED,"
            + "senior-tribunal-caseworker,Y",
        "ST_CIC,CriminalInjuriesCompensation,allocated-administrator,ADMIN,RESTRICTED,hearing-centre-admin,Y",
        "ST_CIC,CriminalInjuriesCompensation,allocated-administrator,ADMIN,RESTRICTED,hearing-centre-team-leader,Y",
        "ST_CIC,CriminalInjuriesCompensation,allocated-judge,JUDICIAL,RESTRICTED,judge,Y",
        "ST_CIC,CriminalInjuriesCompensation,allocated-judge,JUDICIAL,RESTRICTED,fee-paid-judge,Y"
    })
    void shouldGrantAccessFor_CaseRole(String jurisdiction, String caseType, String roleName,
                                       String roleCategory, String classification,
                                       String existingRoleName, String expectedSubstantive) {

        verifyGrantOrRejectAccessFor_CaseRole(
            jurisdiction,
            caseType,
            roleName,
            roleCategory,
            classification,
            existingRoleName,
            expectedSubstantive,
            APPROVED
        );
    }


    @ParameterizedTest
    @CsvSource({
        // EMPLOYMENT ET_EnglandWales
        "EMPLOYMENT,ET_EnglandWales,lead-judge,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales,hearing-judge,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-1,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-2,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales,allocated-admin-caseworker,ADMIN,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales,allocated-ctsc-caseworker,CTSC,PUBLIC",
        // EMPLOYMENT ET_EnglandWales_Multiple
        "EMPLOYMENT,ET_EnglandWales_Multiple,lead-judge,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales_Multiple,hearing-judge,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-1,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-2,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-ctsc-caseworker,CTSC,PUBLIC",
        // EMPLOYMENT ET_Scotland
        "EMPLOYMENT,ET_Scotland,lead-judge,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_Scotland,hearing-judge,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_Scotland,tribunal-member-1,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_Scotland,tribunal-member-2,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_Scotland,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC",
        "EMPLOYMENT,ET_Scotland,allocated-admin-caseworker,ADMIN,PUBLIC",
        "EMPLOYMENT,ET_Scotland,allocated-ctsc-caseworker,CTSC,PUBLIC",
        // EMPLOYMENT ET_Scotland_Multiple
        "EMPLOYMENT,ET_Scotland_Multiple,lead-judge,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_Scotland_Multiple,hearing-judge,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-1,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-2,JUDICIAL,PUBLIC",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-tribunal-caseworker,LEGAL_OPERATIONS,PUBLIC",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-admin-caseworker,ADMIN,PUBLIC",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-ctsc-caseworker,CTSC,PUBLIC",
        // ST_CIC CriminalInjuriesCompensation
        "ST_CIC,CriminalInjuriesCompensation,hearing-judge,JUDICIAL,PUBLIC",
        "ST_CIC,CriminalInjuriesCompensation,interloc-judge,JUDICIAL,PUBLIC",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-1,JUDICIAL,PUBLIC",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-2,JUDICIAL,PUBLIC",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-3,JUDICIAL,PUBLIC",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1,JUDICIAL,PUBLIC",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2,JUDICIAL,PUBLIC",
        "ST_CIC,CriminalInjuriesCompensation,allocated-legal-officer,LEGAL_OPERATIONS,RESTRICTED",
        "ST_CIC,CriminalInjuriesCompensation,allocated-administrator,ADMIN,RESTRICTED",
        "ST_CIC,CriminalInjuriesCompensation,allocated-judge,JUDICIAL,RESTRICTED",
        // PRIVATELAW PRLAPPS
        "PRIVATELAW,PRLAPPS,hearing-judge,JUDICIAL,RESTRICTED",
        "PRIVATELAW,PRLAPPS,allocated-magistrate,JUDICIAL,RESTRICTED",
        "PRIVATELAW,PRLAPPS,allocated-judge,JUDICIAL,RESTRICTED",
        "PRIVATELAW,PRLAPPS,gatekeeping-judge,JUDICIAL,RESTRICTED",
        "PRIVATELAW,PRLAPPS,allocated-legal-adviser,LEGAL_OPERATIONS,RESTRICTED",
        "PRIVATELAW,PRLAPPS,allocated-ctsc-caseworker,CTSC,RESTRICTED",
        "PRIVATELAW,PRLAPPS,allocated-admin-caseworker,ADMIN,RESTRICTED"
    })
    void shouldRejectAccessFor_CaseRole_BadExistingRole(String jurisdiction, String caseType, String roleName,
                                                        String roleCategory, String classification) {
        verifyGrantOrRejectAccessFor_CaseRole(jurisdiction,
                                              caseType,
                                              roleName,
                                              roleCategory,
                                              classification,
                                              "bad-role-name",
                                              null,
                                              REJECTED);
    }

    private void verifyGrantOrRejectAccessFor_CaseRole(String jurisdiction, String caseType, String roleName,
                                                       String roleCategory, String classification,
                                                       String existingRoleName, String expectedSubstantive,
                                                       Status expectedRoleAssignmentStatus) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(getCaseFromMap(jurisdiction, caseType).getId()));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode(caseType));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccessGrant(
            "sscs-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            Classification.valueOf(classification),
            SPECIFIC,
            Status.CREATE_REQUESTED,
            "am_org_role_mapping_service",
            false,
            "Access required for reasons",
            CASE_ALLOCATOR_ID,
            "reference"
        )
            .build();

        setFeatureFlags();

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(caseType));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          TestDataBuilder.CASE_ALLOCATOR_ID,
                                          "case-allocator",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          PUBLIC,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      ),
                                  TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          TestDataBuilder.CASE_ALLOCATOR_ID,
                                          existingRoleName,
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          PUBLIC,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      )
                                )
                          );

        if (expectedRoleAssignmentStatus == APPROVED) {
            assignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
                assertEquals(jurisdiction, roleAssignment.getAttributes().get("jurisdiction").asText());
                assertEquals(caseType, roleAssignment.getAttributes().get("caseType").asText());
                assertEquals(roleName, roleAssignment.getRoleName());
                assertEquals(RoleCategory.valueOf(roleCategory), roleAssignment.getRoleCategory());
                assertEquals(Classification.valueOf(classification), roleAssignment.getClassification());
                if (expectedSubstantive != null) {
                    assertEquals(expectedSubstantive, roleAssignment.getAttributes().get("substantive").asText());
                }
                assertEquals(Status.APPROVED, roleAssignment.getStatus());
            });
        } else {
            assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));
        }
    }

    @ParameterizedTest
    @CsvSource({
        // SSCS Benefit
        "SSCS,Benefit,hearing-judge",
        "SSCS,Benefit,tribunal-member-1",
        "SSCS,Benefit,tribunal-member-2",
        "SSCS,Benefit,tribunal-member-3",
        "SSCS,Benefit,appraiser-1",
        "SSCS,Benefit,appraiser-2",
        "SSCS,Benefit,interloc-judge",
        "SSCS,Benefit,post-hearing-judge",
        "SSCS,Benefit,case-allocator",
        "SSCS,Benefit,registrar",
        "SSCS,Benefit,allocated-tribunal-caseworker",
        "SSCS,Benefit,allocated-admin-caseworker",
        "SSCS,Benefit,allocated-ctsc-caseworker",
        // PRIVATELAW PRLAPPS
        "PRIVATELAW,PRLAPPS,hearing-judge",
        "PRIVATELAW,PRLAPPS,allocated-magistrate",
        "PRIVATELAW,PRLAPPS,allocated-judge",
        "PRIVATELAW,PRLAPPS,gatekeeping-judge",
        "PRIVATELAW,PRLAPPS,allocated-legal-adviser",
        "PRIVATELAW,PRLAPPS,allocated-ctsc-caseworker",
        "PRIVATELAW,PRLAPPS,allocated-admin-caseworker",
        // EMPLOYMENT ET_EnglandWales
        "EMPLOYMENT,ET_EnglandWales,lead-judge",
        "EMPLOYMENT,ET_EnglandWales,hearing-judge",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-1",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-2",
        "EMPLOYMENT,ET_EnglandWales,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_EnglandWales,allocated-admin-caseworker",
        "EMPLOYMENT,ET_EnglandWales,allocated-ctsc-caseworker",
        // EMPLOYMENT ET_EnglandWales_Multiple
        "EMPLOYMENT,ET_EnglandWales_Multiple,lead-judge",
        "EMPLOYMENT,ET_EnglandWales_Multiple,hearing-judge",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-1",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-2",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-admin-caseworker",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-ctsc-caseworker",
        // EMPLOYMENT ET_Scotland
        "EMPLOYMENT,ET_Scotland,lead-judge",
        "EMPLOYMENT,ET_Scotland,hearing-judge",
        "EMPLOYMENT,ET_Scotland,tribunal-member-1",
        "EMPLOYMENT,ET_Scotland,tribunal-member-2",
        "EMPLOYMENT,ET_Scotland,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_Scotland,allocated-admin-caseworker",
        "EMPLOYMENT,ET_Scotland,allocated-ctsc-caseworker",
        // EMPLOYMENT ET_Scotland_Multiple
        "EMPLOYMENT,ET_Scotland_Multiple,lead-judge",
        "EMPLOYMENT,ET_Scotland_Multiple,hearing-judge",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-1",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-2",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-admin-caseworker",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-ctsc-caseworker",
        // ST_CIC CriminalInjuriesCompensation
        "ST_CIC,CriminalInjuriesCompensation,hearing-judge",
        "ST_CIC,CriminalInjuriesCompensation,interloc-judge",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-1",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-2",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2"
    })
    void shouldDelete_CaseRole(String jurisdiction, String caseType, String roleName) {
        verifyGrantOrRejectDeleteFor_CaseRole(jurisdiction, caseType, roleName, null, Status.DELETE_APPROVED);
    }

    @ParameterizedTest
    @CsvSource({
        // SSCS Benefit
        "SSCS,Benefit,hearing-judge",
        "SSCS,Benefit,tribunal-member-1",
        "SSCS,Benefit,tribunal-member-2",
        "SSCS,Benefit,tribunal-member-3",
        "SSCS,Benefit,appraiser-1",
        "SSCS,Benefit,appraiser-2",
        "SSCS,Benefit,interloc-judge",
        "SSCS,Benefit,post-hearing-judge",
        "SSCS,Benefit,case-allocator",
        "SSCS,Benefit,registrar",
        "SSCS,Benefit,allocated-tribunal-caseworker",
        "SSCS,Benefit,allocated-admin-caseworker",
        "SSCS,Benefit,allocated-ctsc-caseworker",
        // PRIVATELAW PRLAPPS
        "PRIVATELAW,PRLAPPS,hearing-judge",
        "PRIVATELAW,PRLAPPS,allocated-magistrate",
        "PRIVATELAW,PRLAPPS,allocated-judge",
        "PRIVATELAW,PRLAPPS,gatekeeping-judge",
        "PRIVATELAW,PRLAPPS,allocated-legal-adviser",
        "PRIVATELAW,PRLAPPS,allocated-ctsc-caseworker",
        "PRIVATELAW,PRLAPPS,allocated-admin-caseworker",
        // EMPLOYMENT ET_EnglandWales
        "EMPLOYMENT,ET_EnglandWales,lead-judge",
        "EMPLOYMENT,ET_EnglandWales,hearing-judge",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-1",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-2",
        "EMPLOYMENT,ET_EnglandWales,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_EnglandWales,allocated-admin-caseworker",
        "EMPLOYMENT,ET_EnglandWales,allocated-ctsc-caseworker",
        // EMPLOYMENT ET_EnglandWales_Multiple
        "EMPLOYMENT,ET_EnglandWales_Multiple,lead-judge",
        "EMPLOYMENT,ET_EnglandWales_Multiple,hearing-judge",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-1",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-2",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-admin-caseworker",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-ctsc-caseworker",
        // EMPLOYMENT ET_Scotland
        "EMPLOYMENT,ET_Scotland,lead-judge",
        "EMPLOYMENT,ET_Scotland,hearing-judge",
        "EMPLOYMENT,ET_Scotland,tribunal-member-1",
        "EMPLOYMENT,ET_Scotland,tribunal-member-2",
        "EMPLOYMENT,ET_Scotland,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_Scotland,allocated-admin-caseworker",
        "EMPLOYMENT,ET_Scotland,allocated-ctsc-caseworker",
        // EMPLOYMENT ET_Scotland_Multiple
        "EMPLOYMENT,ET_Scotland_Multiple,lead-judge",
        "EMPLOYMENT,ET_Scotland_Multiple,hearing-judge",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-1",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-2",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-admin-caseworker",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-ctsc-caseworker",
        // ST_CIC CriminalInjuriesCompensation
        "ST_CIC,CriminalInjuriesCompensation,hearing-judge",
        "ST_CIC,CriminalInjuriesCompensation,interloc-judge",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-1",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-2",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2"
    })
    void shouldDelete_CaseRole_withMatchingCaseAllocatorRegion(String jurisdiction, String caseType, String roleName) {
        verifyGrantOrRejectDeleteFor_CaseRole(jurisdiction, caseType, roleName, CASE_REGION, Status.DELETE_APPROVED);
    }

    @ParameterizedTest
    @CsvSource({
        // SSCS Benefit
        "SSCS,Benefit,hearing-judge",
        "SSCS,Benefit,tribunal-member-1",
        "SSCS,Benefit,tribunal-member-2",
        "SSCS,Benefit,tribunal-member-3",
        "SSCS,Benefit,appraiser-1",
        "SSCS,Benefit,appraiser-2",
        "SSCS,Benefit,interloc-judge",
        "SSCS,Benefit,post-hearing-judge",
        "SSCS,Benefit,case-allocator",
        "SSCS,Benefit,registrar",
        "SSCS,Benefit,allocated-tribunal-caseworker",
        "SSCS,Benefit,allocated-admin-caseworker",
        "SSCS,Benefit,allocated-ctsc-caseworker",
        // PRIVATELAW PRLAPPS
        "PRIVATELAW,PRLAPPS,hearing-judge",
        "PRIVATELAW,PRLAPPS,allocated-magistrate",
        "PRIVATELAW,PRLAPPS,allocated-judge",
        "PRIVATELAW,PRLAPPS,gatekeeping-judge",
        "PRIVATELAW,PRLAPPS,allocated-legal-adviser",
        "PRIVATELAW,PRLAPPS,allocated-ctsc-caseworker",
        "PRIVATELAW,PRLAPPS,allocated-admin-caseworker",
        // EMPLOYMENT ET_EnglandWales
        "EMPLOYMENT,ET_EnglandWales,lead-judge",
        "EMPLOYMENT,ET_EnglandWales,hearing-judge",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-1",
        "EMPLOYMENT,ET_EnglandWales,tribunal-member-2",
        "EMPLOYMENT,ET_EnglandWales,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_EnglandWales,allocated-admin-caseworker",
        "EMPLOYMENT,ET_EnglandWales,allocated-ctsc-caseworker",
        // EMPLOYMENT ET_EnglandWales_Multiple
        "EMPLOYMENT,ET_EnglandWales_Multiple,lead-judge",
        "EMPLOYMENT,ET_EnglandWales_Multiple,hearing-judge",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-1",
        "EMPLOYMENT,ET_EnglandWales_Multiple,tribunal-member-2",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-admin-caseworker",
        "EMPLOYMENT,ET_EnglandWales_Multiple,allocated-ctsc-caseworker",
        // EMPLOYMENT ET_Scotland
        "EMPLOYMENT,ET_Scotland,lead-judge",
        "EMPLOYMENT,ET_Scotland,hearing-judge",
        "EMPLOYMENT,ET_Scotland,tribunal-member-1",
        "EMPLOYMENT,ET_Scotland,tribunal-member-2",
        "EMPLOYMENT,ET_Scotland,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_Scotland,allocated-admin-caseworker",
        "EMPLOYMENT,ET_Scotland,allocated-ctsc-caseworker",
        // EMPLOYMENT ET_Scotland_Multiple
        "EMPLOYMENT,ET_Scotland_Multiple,lead-judge",
        "EMPLOYMENT,ET_Scotland_Multiple,hearing-judge",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-1",
        "EMPLOYMENT,ET_Scotland_Multiple,tribunal-member-2",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-tribunal-caseworker",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-admin-caseworker",
        "EMPLOYMENT,ET_Scotland_Multiple,allocated-ctsc-caseworker",
        // ST_CIC CriminalInjuriesCompensation
        "ST_CIC,CriminalInjuriesCompensation,hearing-judge",
        "ST_CIC,CriminalInjuriesCompensation,interloc-judge",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-1",
        "ST_CIC,CriminalInjuriesCompensation,tribunal-member-2",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-1",
        "ST_CIC,CriminalInjuriesCompensation,appraiser-2"
    })
    void shouldRejectDelete_CaseRole_withBadCaseAllocatorRegion(String jurisdiction, String caseType, String roleName) {
        verifyGrantOrRejectDeleteFor_CaseRole(jurisdiction, caseType, roleName, "bad-region", Status.DELETE_REJECTED);
    }

    private void verifyGrantOrRejectDeleteFor_CaseRole(String jurisdiction,
                                                       String caseType,
                                                       String roleName,
                                                       String caRegion,
                                                       Status expectedRoleAssignmentStatus) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode(caseType));
        roleAssignmentAttributes.put("caseId", convertValueJsonNode(getCaseFromMap(jurisdiction, caseType).getId()));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccessGrant(
            "delete-access",
            roleName,
            RoleCategory.valueOf(RoleCategory.JUDICIAL.name()),
            RoleType.CASE,
            roleAssignmentAttributes,
            PUBLIC,
            SPECIFIC,
            DELETE_REQUESTED,
            "am_org_role_mapping_service",
            false,
            "Delete required for reasons",
            CASE_ALLOCATOR_ID,
            "reference"
        )
            .build();

        setFeatureFlags();

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(caseType));
        if (StringUtils.isNotBlank(caRegion)) {
            existingAttributes.put("region", convertValueJsonNode(caRegion));
        }
        existingAttributes.put("allocatedRole", convertValueJsonNode(roleName));

        executeDroolRules(List.of(buildExistingRole(CASE_ALLOCATOR_ID,
                                                    "case-allocator",
                                                    RoleCategory.JUDICIAL,
                                                    existingAttributes,
                                                    RoleType.CASE,
                                                    PUBLIC,
                                                    GrantType.STANDARD,
                                                    Status.LIVE
        )));

        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(expectedRoleAssignmentStatus, ra.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "judge,JUDICIAL,judge,SSCS,Benefit,ORGANISATION",
        "hearing-judge,ADMIN,fee-paid-judge,SSCS,Benefit,ORGANISATION",
        "interloc-judge,JUDICIAL,judge,SSCS,Asylum,ORGANISATION",
    })
    void shouldRejectAccessFor_SSCS_CaseRole(String roleName, String roleCategory, String existingRoleName,
                                             String jurisdiction, String caseType, String roleType) {

        HashMap<String, JsonNode> roleAssignmentAttributes = new HashMap<>();
        roleAssignmentAttributes.put("caseId", convertValueJsonNode("1212121212121212"));
        roleAssignmentAttributes.put("requestedRole", convertValueJsonNode(roleName));
        roleAssignmentAttributes.put("caseType", convertValueJsonNode(caseType));
        roleAssignmentAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccessGrant(
            "sscs-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.CASE,
            roleAssignmentAttributes,
            PUBLIC,
            SPECIFIC,
            Status.CREATE_REQUESTED,
            "am_org_role_mapping_service",
            false,
            "Access required for reasons",
            CASE_ALLOCATOR_ID,
            "reference"
        )
            .build();

        setFeatureFlags();

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode(jurisdiction));
        existingAttributes.put("caseType", convertValueJsonNode(caseType));

        executeDroolRules(List.of(TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          TestDataBuilder.CASE_ALLOCATOR_ID,
                                          "case-allocator",
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          PUBLIC,
                                          GrantType.STANDARD,
                                          RoleType.ORGANISATION
                                      ),
                                  TestDataBuilder
                                      .buildExistingRoleForDrools(
                                          TestDataBuilder.CASE_ALLOCATOR_ID,
                                          existingRoleName,
                                          RoleCategory.valueOf(roleCategory),
                                          existingAttributes,
                                          PUBLIC,
                                          GrantType.STANDARD,
                                          RoleType.valueOf(roleType)
                                      )
                          )
        );

        assignmentRequest.getRequestedRoles().forEach(roleAssignment ->
                                                          assertEquals(Status.REJECTED, roleAssignment.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "interloc-judge,JUDICIAL,am_role_assignment_service,ORGANISATION",
        "hearing-judge,JUDICIAL,am_org_role_mapping_service,CASE"
    })
    void shouldRejectDeleteRequest_SSCS_CaseRole(String roleName,
                                                 String roleCategory,
                                                 String clientId,
                                                 String roleType) {

        HashMap<String, JsonNode> existingAttributes = new HashMap<>();
        existingAttributes.put("jurisdiction", convertValueJsonNode("SSCS"));
        existingAttributes.put("caseType", convertValueJsonNode("Benefit"));
        existingAttributes.put("caseId", convertValueJsonNode("1212121212121212"));
        existingAttributes.put("requestedRole", convertValueJsonNode(roleName));

        assignmentRequest = TestDataBuilder.buildAssignmentRequestSpecialAccessGrant(
            "delete-access",
            roleName,
            RoleCategory.valueOf(roleCategory),
            RoleType.valueOf(roleType),
            existingAttributes,
            PUBLIC,
            SPECIFIC,
            DELETE_REQUESTED,
            clientId,
            false,
            "Delete required for reasons",
            CASE_ALLOCATOR_ID,
            "reference"
        )
            .build();

        setFeatureFlags();

        executeDroolRules(List.of(buildExistingRole(CASE_ALLOCATOR_ID,
                                                    roleName,
                                                    RoleCategory.JUDICIAL,
                                                    existingAttributes,
                                                    RoleType.CASE,
                                                    PUBLIC,
                                                    GrantType.STANDARD,
                                                    Status.LIVE)));

        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_REJECTED, ra.getStatus()));
    }

    private void setFeatureFlags() {
        List<String> flags = List.of("sscs_wa_1_0", "sscs_case_allocator_1_0", "all_wa_services_case_allocator_1_0");

        for (String flag : flags) {
            featureFlags.add(
                FeatureFlag.builder().flagName(flag).status(true).build()
            );
        }
    }
}
