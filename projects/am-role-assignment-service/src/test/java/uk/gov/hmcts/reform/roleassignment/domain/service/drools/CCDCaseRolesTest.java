package uk.gov.hmcts.reform.roleassignment.domain.service.drools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.domain.model.FeatureFlag;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.FeatureFlagEnum;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.RetrieveDataService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType.SPECIFIC;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.getRequestedCaseRole_ra;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

@RunWith(MockitoJUnitRunner.class)
class CCDCaseRolesTest extends DroolBase {

    @Test
    void shouldRejectCaseRequestedRolesForUnauthoriseRequest() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.PROFESSIONAL,
                                   "[PETSOLICITOR]", SPECIFIC, "caseId",
                                 "1234567890123456", CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        FeatureFlag featureFlag  =  FeatureFlag.builder()
            .status(true).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));

        //verify retrieveDataService is used as ClientId = null
        RetrieveDataService retrieveDataService = getRetrieveDataService();
        verify(retrieveDataService).getCaseById("1234567890123456");
    }

    @Test
    void shouldRejectCaseRequestedRolesForUnauthoriseRequestNoLoadCaseData() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.PROFESSIONAL,
                                                                "[PETSOLICITOR]", SPECIFIC, "caseId",
                                                                "1234567890123456", CREATE_REQUESTED);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("ccd_data");
        FeatureFlag featureFlag  =  FeatureFlag.builder()
            .status(true).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));

        //verify retrieveDataService is not used as ClientId = ccd_data
        RetrieveDataService retrieveDataService = getRetrieveDataService();
        verifyNoInteractions(retrieveDataService);
    }

    @Test
    void shouldApproveCreaterCaseRole() {
        verifyCreateCaseRequestedRole_CCD_1_0("[CREATOR]", "ccd_data", RoleCategory.JUDICIAL);
        verifyCreateCaseRequestedRole_CCD_1_0("[CREATOR]", "aac_manage_case_assignment",
                                              RoleCategory.LEGAL_OPERATIONS);
        verifyCreateCaseRequestedRole_CCD_1_0("[CREATOR]", "aac_manage_case_assignment",
                                              RoleCategory.CITIZEN);

        //verify retrieveDataService is not used as ClientId = ccd_data or aac_manage_case_assignment
        RetrieveDataService retrieveDataService = getRetrieveDataService();
        verifyNoInteractions(retrieveDataService);
    }

    @Test
    void shouldApprovePetSolicitorCaseRole() {
        verifyCreateCaseRequestedRole_CCD_1_0("[PETSOLICITOR]", "ccd_data", RoleCategory.PROFESSIONAL);
    }

    private void verifyCreateCaseRequestedRole_CCD_1_0(String roleName, String clientId, RoleCategory category) {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(category, roleName,
                                                             SPECIFIC, "caseId",
                                                             "1234567890123456", CREATE_REQUESTED);
        requestedRole1.setClassification(Classification.RESTRICTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId(clientId);

        FeatureFlag featureFlag  =  FeatureFlag.builder().build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));
    }

    @Test
    void shouldApproveDummyCaseRoleCreation_CCD_1_0_enableByPassDroolRule() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.SYSTEM, "[RESPSOLICITOR]",
                                                             SPECIFIC, "caseId",
                                                             "1234567890123456", CREATE_REQUESTED);
        requestedRole1.setClassification(Classification.RESTRICTED);
        requestedRole1.getAttributes().put("jurisdiction", convertValueJsonNode("BEFTA_MASTER"));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("ccd_data");

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.CCD_BYPASS_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));

        //verify retrieveDataService is not used as ClientId = ccd_data
        RetrieveDataService retrieveDataService = getRetrieveDataService();
        verifyNoInteractions(retrieveDataService);
    }

    @Test
    void shouldApproveDummyCaseRoleCreationWithDummyRoleName_CCD_1_0_enableByPassDroolRule() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.PROFESSIONAL, "[DUMMYSOLICITOR]",
                                                             SPECIFIC, "caseId",
                                                             "1234567890123456", CREATE_REQUESTED);
        requestedRole1.setClassification(Classification.PUBLIC);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("AUTOTEST1"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("ccd_data");
        assignmentRequest.getRequest().setByPassOrgDroolRule(true);

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.CCD_BYPASS_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));

        //verify retrieveDataService is not used as ClientId = ccd_data
        RetrieveDataService retrieveDataService = getRetrieveDataService();
        verifyNoInteractions(retrieveDataService);
    }

    @Test
    void shouldRejectDummyCaseRoleCreation_CCD_1_0_disableByPassDroolRule() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.SYSTEM, "[RESPSOLICITOR]",
                                                             SPECIFIC, "caseId",
                                                             "1234567890123456", CREATE_REQUESTED);
        requestedRole1.setClassification(Classification.RESTRICTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("ccd_data");
        assignmentRequest.getRequest().setByPassOrgDroolRule(false);

        FeatureFlag featureFlag  =  FeatureFlag.builder().build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));
    }

    @Test
    void shouldApproveDeletePetsolicitorCaserole() {
        verifyDeleteCaseRequestRole_CCD_1_0("[PETSOLICITOR]", "ccd_data", RoleCategory.PROFESSIONAL);
        verifyDeleteCaseRequestRole_CCD_1_0("[PETSOLICITOR]", "ccd_case_disposer", RoleCategory.PROFESSIONAL);
    }

    @Test
    void shouldApproveDeleteCreaterCaseRequestedRoles() {
        verifyDeleteCaseRequestRole_CCD_1_0("[CREATOR]", "ccd_data", RoleCategory.JUDICIAL);
        verifyDeleteCaseRequestRole_CCD_1_0("[CREATOR]", "aac_manage_case_assignment",
                                            RoleCategory.LEGAL_OPERATIONS);
        verifyDeleteCaseRequestRole_CCD_1_0("[CREATOR]", "aac_manage_case_assignment",
                                            RoleCategory.CITIZEN);
        verifyDeleteCaseRequestRole_CCD_1_0("[DUMMYCREATOR]", "ccd_data", RoleCategory.PROFESSIONAL);
    }

    private void verifyDeleteCaseRequestRole_CCD_1_0(String roleName, String clientId, RoleCategory category) {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(category, roleName,
                                                             SPECIFIC, "caseId",
                                                             "1234567890123456", DELETE_REQUESTED);
        requestedRole1.setClassification(Classification.RESTRICTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId(clientId);

        FeatureFlag featureFlag  =  FeatureFlag.builder().build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_APPROVED, ra.getStatus()));
    }

    @Test
    void shouldApproveDeleteDummyCaseRoles_enableCcdBypassFlag() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.SYSTEM, "[RESPSOLICITOR]",
                                                             SPECIFIC, "caseId",
                                                             "1234567890123456", DELETE_REQUESTED);
        requestedRole1.setClassification(Classification.RESTRICTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("AUTOTEST1"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("ccd_data");

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.CCD_BYPASS_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_APPROVED, ra.getStatus()));

        //verify retrieveDataService is not used as ClientId = ccd_data
        RetrieveDataService retrieveDataService = getRetrieveDataService();
        verifyNoInteractions(retrieveDataService);
    }

    @Test
    void shouldApproveDeleteDummyCaseRolesWithDummyRoleName_enableCcdBypassFlag() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.PROFESSIONAL, "[DUMMYSOLICITOR]",
                                                             SPECIFIC, "caseId",
                                                             "1234567890123456", DELETE_REQUESTED);
        requestedRole1.setClassification(Classification.RESTRICTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("AUTOTEST1"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("ccd_data");

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.CCD_BYPASS_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_APPROVED, ra.getStatus()));
    }

    @Test
    void shouldRejectDeleteDummyCaseRoles_invalidJurisdiction() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.SYSTEM, "[RESPSOLICITOR]",
                                                             SPECIFIC, "caseId",
                                                             "1234567890123456", DELETE_REQUESTED);
        requestedRole1.setClassification(Classification.RESTRICTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("ccd_data");

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.CCD_BYPASS_1_0.getValue())
            .status(true).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_REJECTED, ra.getStatus()));
    }

    @Test
    void shouldRejectCaseRoleCreation_disableCcdBypassFlag() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.PROFESSIONAL,
                                                               "[PETSOLICITOR]", SPECIFIC, "caseId",
                                                             "1234567890123456", CREATE_REQUESTED);
        requestedRole1.setClassification(Classification.RESTRICTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("AUTOTEST1"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("ccd_data");

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.CCD_BYPASS_1_0.getValue())
            .status(false).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));
    }

    @Test
    void shouldApproveCaseRoleCreation_enableByPassDroolRule_enableFlag() {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(RoleCategory.PROFESSIONAL,
                                                               "[PETSOLICITOR]", SPECIFIC, "caseId",
                                                             "1234567890123456", CREATE_REQUESTED);
        requestedRole1.setClassification(Classification.RESTRICTED);
        requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                     "caseType", convertValueJsonNode("Asylum"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId("ccd_data");

        FeatureFlag featureFlag  =  FeatureFlag.builder().build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));

        //verify retrieveDataService is not used as ClientId = ccd_data
        RetrieveDataService retrieveDataService = getRetrieveDataService();
        verifyNoInteractions(retrieveDataService);
    }

    @Test
    void shouldApprovePrivateLawSolicitorCaseRoles() {
        verifyCreatePrivateLawCaseRequestedRole("[C100APPLICANTSOLICITOR1]");
        verifyCreatePrivateLawCaseRequestedRole("[C100APPLICANTSOLICITOR2]");
        verifyCreatePrivateLawCaseRequestedRole("[C100APPLICANTSOLICITOR3]");
        verifyCreatePrivateLawCaseRequestedRole("[C100APPLICANTSOLICITOR4]");
        verifyCreatePrivateLawCaseRequestedRole("[C100APPLICANTSOLICITOR5]");
        verifyCreatePrivateLawCaseRequestedRole("[FL401APPLICANTSOLICITOR]");
        verifyCreatePrivateLawCaseRequestedRole("[C100CHILDSOLICITOR1]");
        verifyCreatePrivateLawCaseRequestedRole("[C100CHILDSOLICITOR2]");
        verifyCreatePrivateLawCaseRequestedRole("[C100CHILDSOLICITOR3]");
        verifyCreatePrivateLawCaseRequestedRole("[C100CHILDSOLICITOR4]");
        verifyCreatePrivateLawCaseRequestedRole("[C100CHILDSOLICITOR5]");
        verifyCreatePrivateLawCaseRequestedRole("[C100RESPONDENTSOLICITOR1]");
        verifyCreatePrivateLawCaseRequestedRole("[C100RESPONDENTSOLICITOR2]");
        verifyCreatePrivateLawCaseRequestedRole("[C100RESPONDENTSOLICITOR3]");
        verifyCreatePrivateLawCaseRequestedRole("[C100RESPONDENTSOLICITOR4]");
        verifyCreatePrivateLawCaseRequestedRole("[C100RESPONDENTSOLICITOR5]");
        verifyCreatePrivateLawCaseRequestedRole("[FL401RESPONDENTSOLICITOR]");
    }

    private void verifyCreatePrivateLawCaseRequestedRole(String roleName) {
        RoleAssignment requestedRole = getRequestedCaseRole_ra(
            RoleCategory.PROFESSIONAL,
            roleName,
            SPECIFIC,
            "caseId",
            "1234567890123456",
            CREATE_REQUESTED
        );
        requestedRole.setClassification(Classification.RESTRICTED);
        requestedRole.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("PRIVATELAW"),
                                                     "caseType", convertValueJsonNode("PRLAPPS"),
                                                     "caseId", convertValueJsonNode("1234567890123456")));
        assignmentRequest.setRequestedRoles(List.of(requestedRole));
        assignmentRequest.getRequest().setClientId("ccd_data");

        FeatureFlag featureFlag  =  FeatureFlag.builder().build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));
    }

    @Nested
    @DisplayName("IDAM Disposer CCD Case Roles Tests")
    class IdamDisposerCaseRolesTest {

        static final String IDAM_DISPOSER_CLIENT_ID = "disposer-idam-user";

        @Test
        void shouldApproveCreateCaseRoleForCitizenFromIdamDisposer() {

            // GIVEN
            assignmentRequest.setRequestedRoles(List.of(createRequestedRole(RoleCategory.CITIZEN)));
            assignmentRequest.getRequest().setClientId(IDAM_DISPOSER_CLIENT_ID);
            setDisposerFeatureFlag(true);

            // WHEN
            buildExecuteKieSession();

            // THEN
            assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));

            //verify retrieveDataService is not used as ClientId = disposer-idam-user
            RetrieveDataService retrieveDataService = getRetrieveDataService();
            verifyNoInteractions(retrieveDataService);
        }

        @ParameterizedTest
        @EnumSource(
            value = RoleCategory.class,
            names = { "CITIZEN" },
            mode = EnumSource.Mode.EXCLUDE
        )
        void shouldRejectCreateCaseRoleForNonCitizenFromIdamDisposer(RoleCategory roleCategory) {

            // GIVEN
            assignmentRequest.setRequestedRoles(List.of(createRequestedRole(roleCategory)));
            assignmentRequest.getRequest().setClientId(IDAM_DISPOSER_CLIENT_ID);
            setDisposerFeatureFlag(true);

            // WHEN
            buildExecuteKieSession();

            // THEN
            assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));

            //verify retrieveDataService is not used as ClientId = disposer-idam-user
            RetrieveDataService retrieveDataService = getRetrieveDataService();
            verifyNoInteractions(retrieveDataService);
        }

        @Test
        void shouldRejectCreateCaseRoleForCitizenFromIdamDisposer_flagDisabled() {

            // GIVEN
            assignmentRequest.setRequestedRoles(List.of(createRequestedRole(RoleCategory.CITIZEN)));
            assignmentRequest.getRequest().setClientId(IDAM_DISPOSER_CLIENT_ID);
            setDisposerFeatureFlag(false); // i.e. disable flag

            // WHEN
            buildExecuteKieSession();

            // THEN
            assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));

            //verify retrieveDataService is not used as ClientId = disposer-idam-user
            RetrieveDataService retrieveDataService = getRetrieveDataService();
            verifyNoInteractions(retrieveDataService);
        }

        private RoleAssignment createRequestedRole(RoleCategory roleCategory) {
            RoleAssignment requestedRole1 = getRequestedCaseRole_ra(roleCategory,
                                                                    "[CREATOR]", SPECIFIC, "caseId",
                                                                    "1234567890123456", CREATE_REQUESTED);
            requestedRole1.setClassification(Classification.RESTRICTED);
            requestedRole1.getAttributes().putAll(Map.of("jurisdiction", convertValueJsonNode("IA"),
                                                         "caseType", convertValueJsonNode("Asylum"),
                                                         "caseId", convertValueJsonNode("1234567890123456")));

            return requestedRole1;
        }

        private void setDisposerFeatureFlag(boolean status) {
            FeatureFlag featureFlag  =  FeatureFlag.builder()
                .flagName(FeatureFlagEnum.DISPOSER_1_0.getValue())
                .status(status)
                .build();

            featureFlags.add(featureFlag);
        }
    }

}
