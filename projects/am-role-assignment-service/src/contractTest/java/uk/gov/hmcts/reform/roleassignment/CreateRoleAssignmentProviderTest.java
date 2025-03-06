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
import uk.gov.hmcts.reform.roleassignment.controller.endpoints.CreateAssignmentController;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.FeatureFlagEnum;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.createroles.CreateRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.feignclients.DataStoreApi;
import uk.gov.hmcts.reform.roleassignment.util.CorrelationInterceptorUtil;
import uk.gov.hmcts.reform.roleassignment.util.JacksonUtils;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder.buildAttributesFromFile;

@ExtendWith(SpringExtension.class)
@Provider("am_roleAssignment_createAssignment")
@PactBroker(scheme = "${PACT_BROKER_SCHEME:http}",
    host = "${PACT_BROKER_URL:localhost}", port = "${PACT_BROKER_PORT:9292}", consumerVersionSelectors = {
        @VersionSelector(tag = "master")})
@TestPropertySource(properties = {"org.request.byPassOrgDroolRule=true", "roleassignment.query.size=20",
    "spring.cache.type=none", "launchdarkly.sdk.environment=pr"})
@Import(RoleAssignmentProviderTestConfiguration.class)
@IgnoreNoPactsToVerify
public class CreateRoleAssignmentProviderTest {

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private CorrelationInterceptorUtil correlationInterceptorUtil;

    @Autowired
    private CreateRoleAssignmentOrchestrator createRoleAssignmentOrchestrator;

    @Autowired
    private DataStoreApi dataStoreApi;


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
        testTarget.setControllers(new CreateAssignmentController(
            createRoleAssignmentOrchestrator
        ));
        if (context != null) {
            context.setTarget(testTarget);
        }

    }

    @State({"The assignment request is valid with one requested role and replaceExisting flag as false"})
    public void createRoleAssignmentReplaceExistingFalse() {
        setInitMock();
    }

    @State({"The assignment request is valid with one requested role and replaceExisting flag as true"})
    public void createRoleAssignmentReplaceExistingTrue() {
        setInitMock();
    }

    @State({"The assignment request is valid with zero requested role and replaceExisting flag as true"})
    public void createRoleAssignmentZeroRole() {
        setInitMock();
    }

    private void setInitMock() {

        JsonNode attributes = buildAttributesFromFile("attributesCase.json");
        Map<String, JsonNode> attributeMap = JacksonUtils.convertValue(attributes);
        List<Assignment> assignmentList  = List.of(
            ExistingRoleAssignment.builder().actorId("14a21569-eb80-4681-b62c-6ae2ed069e5f")
                .roleType(RoleType.ORGANISATION).roleName("tribunal-caseworker").attributes(attributeMap)
                .status(Status.APPROVED).build(),
            ExistingRoleAssignment.builder().actorId("3168da13-00b3-41e3-81fa-cbc71ac28a0f")
                .roleType(RoleType.ORGANISATION).roleName("case-allocator").attributes(attributeMap)
                .roleCategory(RoleCategory.LEGAL_OPERATIONS).classification(Classification.PUBLIC)
                .status(Status.APPROVED).build()
        );
        when(persistenceService.persistRequest(any())).thenReturn(createEntity());
        doReturn(assignmentList).when(persistenceService)
            .retrieveRoleAssignmentsByQueryRequest(any(), anyInt(), anyInt(), any(), any(), anyBoolean());
        when(persistenceService.getStatusByParam(FeatureFlagEnum.IAC_1_1.getValue(), "pr")).thenReturn(true);
        when(dataStoreApi.getCaseDataV2(anyString())).thenReturn(Case.builder().id("1212121212121213").jurisdiction(
            "IA").caseTypeId("Asylum").securityClassification(Classification.PUBLIC).build());
        when(securityUtils.getUserId()).thenReturn("3168da13-00b3-41e3-81fa-cbc71ac28a0f");
        when(correlationInterceptorUtil.preHandle(any())).thenReturn("14a21569-eb80-4681-b62c-6ae2ed069e2d");
    }

    public RequestEntity createEntity() {
        return RequestEntity.builder()
            .correlationId("123")
            .id(UUID.fromString("c3552563-80e1-49a1-9dc9-b2625e7c44dc"))
            .authenticatedUserId("3168da13-00b3-41e3-81fa-cbc71ac28a0f")
            .clientId("am_org_role_mapping_service")
            .created(LocalDateTime.now())
            .status(Status.APPROVED.toString())
            .build();

    }
}
