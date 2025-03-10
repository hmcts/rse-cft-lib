package uk.gov.hmcts.reform.roleassignment;


import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.VersionSelector;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.roleassignment.controller.endpoints.DeleteAssignmentController;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.FeatureFlagEnum;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.deleteroles.DeleteRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.feignclients.DataStoreApi;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;
import uk.gov.hmcts.reform.roleassignment.util.JacksonUtils;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.buildAttributesFromFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(SpringExtension.class)
@Provider("am_roleAssignment_deleteAssignment")
@PactBroker(scheme = "${PACT_BROKER_SCHEME:http}",
    host = "${PACT_BROKER_URL:localhost}", port = "${PACT_BROKER_PORT:9292}", consumerVersionSelectors = {
        @VersionSelector(tag = "master")})
@TestPropertySource(properties = {"roleassignment.query.size=20", "launchdarkly.sdk.environment=pr",
    "spring.cache.type=none"})
@Import(RoleAssignmentProviderTestConfiguration.class)
@IgnoreNoPactsToVerify
public class DeleteRoleAssignmentProviderTest {

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private DataStoreApi dataStoreApi;

    @Autowired
    private DeleteRoleAssignmentOrchestrator deleteRoleAssignmentOrchestrator;

    private static final String ASSIGNMENT_ID = "704c8b1c-e89b-436a-90f6-953b1dc40157";
    private static final String AUTH_USER_ID = "3168da13-00b3-41e3-81fa-cbc71ac28a0f";

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        if (context != null) {
            context.verifyInteraction();
        }
    }

    @BeforeEach
    void beforeCreate(PactVerificationContext context) {
        MockMvcTestTarget testTarget = new MockMvcTestTarget();
        //System.getProperties().setProperty("pact.verifier.publishResults", "true");
        testTarget.setControllers(new DeleteAssignmentController(
            deleteRoleAssignmentOrchestrator
        ));
        if (context != null) {
            context.setTarget(testTarget);
        }
    }

    @State({"An actor with provided id is available in role assignment service"})
    public void deleteRoleAssignmentById() {
        initMocksId();
    }

    @State({"An actor with provided process & reference is available in role assignment service"})
    public void deleteRoleAssignmentByPr() {
        initMocksPr();
    }

    @State({"Delete the set of selected role assignments as per given delete by query request"})
    public void advanceDeleteQueryWithSuccess() {
        setInitMockAdvanceDelete();
    }

    private void initMocksId() {
        initCommonMocks();
        when(persistenceService.getAssignmentById(UUID.fromString(ASSIGNMENT_ID)))
            .thenReturn(roleAssignmentList());

    }

    private void initMocksPr() {
        initCommonMocks();
        when(persistenceService.getAssignmentsByProcess("p2", "r2", Status.LIVE.toString()))
            .thenReturn(roleAssignmentList());

    }

    private void initCommonMocks() {
        when(persistenceService.persistRequest(any()))
            .thenReturn(TestDataBuilder.buildRequestEntity(
                TestDataBuilder.buildRequest(Status.LIVE, false)));
        when(securityUtils.getServiceName()).thenReturn("am_org_role_mapping_service");
        when(persistenceService.getStatusByParam(FeatureFlagEnum.IAC_1_1.getValue(), "pr")).thenReturn(true);
        when(persistenceService.getStatusByParam(FeatureFlagEnum.ALL_WA_SERVICES_CASE_ALLOCATOR_1_0.getValue(), "pr"))
            .thenReturn(true);
        when(securityUtils.getUserId()).thenReturn(AUTH_USER_ID);

        JsonNode attributes = buildAttributesFromFile("attributesCase.json");
        Map<String, JsonNode> attributeMap = JacksonUtils.convertValue(attributes);
        List<Assignment> assignmentList  = List.of(
            ExistingRoleAssignment.builder().actorId(AUTH_USER_ID).roleCategory(RoleCategory.LEGAL_OPERATIONS)
                .roleType(RoleType.ORGANISATION).roleName("case-allocator").attributes(attributeMap)
                .status(Status.APPROVED).build()
        );
        when(persistenceService.persistRequest(any())).thenReturn(createEntity());
        doReturn(assignmentList).when(persistenceService)
            .retrieveRoleAssignmentsByQueryRequest(any(), anyInt(), anyInt(), any(), any(), anyBoolean());
        when(persistenceService.getTotalRecords()).thenReturn(1L);

        when(dataStoreApi.getCaseDataV2(anyString())).thenReturn(Case.builder().id("1212121212121213").jurisdiction(
            "IA").caseTypeId("Asylum").build());
    }

    private List<RoleAssignment> roleAssignmentList() {
        List<RoleAssignment> roleAssignments = TestDataBuilder.buildRoleAssignmentList_Custom(Status.LIVE,"1234",
                                            "attributesCase.json", RoleType.CASE, "tribunal-caseworker");
        roleAssignments.forEach(r -> r.setGrantType(GrantType.SPECIFIC));
        return roleAssignments;
    }

    private void setInitMockAdvanceDelete() {
        Request deleteRequest = TestDataBuilder.buildRequest(Status.LIVE, false);

        when(securityUtils.getServiceName()).thenReturn("am_org_role_mapping_service");
        when(persistenceService.persistRequest(any())).thenReturn(TestDataBuilder.buildRequestEntity(deleteRequest));
    }

    public RequestEntity createEntity() {
        return RequestEntity.builder()
            .correlationId("123")
            .id(UUID.fromString("c3552563-80e1-49a1-9dc9-b2625e7c44dc"))
            .authenticatedUserId(AUTH_USER_ID)
            .clientId("am_org_role_mapping_service")
            .created(LocalDateTime.now())
            .status(Status.APPROVED.toString())
            .build();

    }

}
