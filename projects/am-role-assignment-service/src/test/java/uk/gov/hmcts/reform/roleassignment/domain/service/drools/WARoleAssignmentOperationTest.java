package uk.gov.hmcts.reform.roleassignment.domain.service.drools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.domain.model.FeatureFlag;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.FeatureFlagEnum;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType.SPECIFIC;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory.LEGAL_OPERATIONS;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REJECTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.getRequestedCaseRole_ra;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.getRequestedOrgRole_ra;
import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.convertValueJsonNode;

@RunWith(MockitoJUnitRunner.class)
class WARoleAssignmentOperationTest extends DroolBase {


    @ParameterizedTest
    @CsvSource({
        "wa_workflow_api,DummyRole,WA,true",
        "wa_task_management_api,DummyRole,WA,true",
        "wa_task_monitor,DummyRole,WA,true",
        "wa_case_event_handler,DummyRole,WA,true",
        "wa_workflow_api,tribunal-caseworker,WA,true",
    })
    void shouldApproveWACaseRoleCreation_PositiveScenarios(String serviceId,
                                                           String roleName,
                                                           String jurisdiction,
                                                           Boolean flagValue) {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(LEGAL_OPERATIONS, roleName, SPECIFIC,
                                                                "caseId","1234567890123456",
                                                                CREATE_REQUESTED);
        requestedRole1.setClassification(Classification.PUBLIC);
        requestedRole1.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId(serviceId);

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.WA_BYPASS_1_0.getValue())
            .status(flagValue).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "wa_workflow_api,DummyRole,WA,false",
        "wa_task_management_api,DummyRole,WA1,true",
        "ccd_data,DummyRole,WA,true",
        "ccd_data,case-worker,WA,true"
    })
    void shouldRejectWACaseRoleCreation_NegativeScenarios(String serviceId,
                                                          String roleName,
                                                          String jurisdiction,
                                                          Boolean flagValue) {
        RoleAssignment requestedRole1 = getRequestedCaseRole_ra(LEGAL_OPERATIONS, roleName, SPECIFIC,
                                                                "caseId","1234567890123456",
                                                                CREATE_REQUESTED);
        requestedRole1.setClassification(Classification.PUBLIC);
        requestedRole1.getAttributes().put("jurisdiction", convertValueJsonNode(jurisdiction));
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId(serviceId);

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.WA_BYPASS_1_0.getValue())
            .status(flagValue).build();
        featureFlags.add(featureFlag);

        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.REJECTED, ra.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "wa_workflow_api,DummyRole,WA,true",
        "wa_workflow_api,case-worker,WA,true"
    })
    void shouldApproveWAOrgRoleCreation_PositiveScenarios(String serviceId,
                                                          String roleName,
                                                          String jurisdiction,
                                                          Boolean flagValue) {
        RoleAssignment requestedRole1 = getRequestedOrgRole_ra(LEGAL_OPERATIONS,
                                                               roleName, SPECIFIC, "jurisdiction",
                                                               jurisdiction, CREATE_REQUESTED,
                                                               Classification.PUBLIC, true);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId(serviceId);

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.WA_BYPASS_1_0.getValue())
            .status(flagValue).build();
        featureFlags.add(featureFlag);
        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.APPROVED, ra.getStatus()));
    }


    @ParameterizedTest
    @CsvSource({
        "wa_workflow_api,DummyRole,WA,true",
        "wa_task_management_api,DummyRole,WA,true",
        "wa_task_monitor,DummyRole,WA,true",
        "wa_case_event_handler,DummyRole,WA,true",
        "wa_workflow_api,tribunal-caseworker,WA,true",
    })
    void shouldApproveWARoleDeletion_PositiveScenarios(String serviceId,
                                                       String roleName,
                                                       String jurisdiction,
                                                       Boolean flagValue) {
        RoleAssignment requestedRole1 = getRequestedOrgRole_ra(LEGAL_OPERATIONS,
                                                               roleName, SPECIFIC, "jurisdiction",
                                                               jurisdiction, DELETE_REQUESTED,
                                                               Classification.PUBLIC, true);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId(serviceId);

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.WA_BYPASS_1_0.getValue())
            .status(flagValue).build();
        featureFlags.add(featureFlag);
        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(Status.DELETE_APPROVED, ra.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({
        "wa_workflow_api,DummyRole,WA,false",
        "wa_workflow_api,DummyRole,WA1,true",
        "ccd_data,DummyRole,WA,true"
    })
    void shouldRejectWARoleDeletion_NegativeScenarios(String serviceId,
                                                       String roleName,
                                                       String jurisdiction,
                                                       Boolean flagValue) {
        RoleAssignment requestedRole1 = getRequestedOrgRole_ra(LEGAL_OPERATIONS,
                                                               roleName, SPECIFIC, "jurisdiction",
                                                               jurisdiction, DELETE_REQUESTED,
                                                               Classification.PUBLIC, true);
        assignmentRequest.setRequestedRoles(List.of(requestedRole1));
        assignmentRequest.getRequest().setClientId(serviceId);

        FeatureFlag featureFlag  =  FeatureFlag.builder().flagName(FeatureFlagEnum.WA_BYPASS_1_0.getValue())
            .status(flagValue).build();
        featureFlags.add(featureFlag);
        buildExecuteKieSession();
        //assertion
        assignmentRequest.getRequestedRoles().forEach(ra -> assertEquals(DELETE_REJECTED, ra.getStatus()));
    }

}
