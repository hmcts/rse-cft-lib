package uk.gov.hmcts.reform.roleassignment;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.VersionSelector;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.roleassignment.controller.endpoints.GetAssignmentController;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.getroles.RetrieveRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@Provider("am_roleAssignment_getAssignment")
@PactBroker(scheme = "${PACT_BROKER_SCHEME:http}",
    host = "${PACT_BROKER_URL:localhost}", port = "${PACT_BROKER_PORT:9292}", consumerVersionSelectors = {
        @VersionSelector(tag = "master")})
@TestPropertySource(properties = {"roleassignment.query.size=20"})
@Import(RoleAssignmentProviderTestConfiguration.class)
@IgnoreNoPactsToVerify
public class GetActorByIdRoleAssignmentProviderTest {

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private RetrieveRoleAssignmentOrchestrator retrieveRoleAssignmentServiceMock;

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        if (context != null) {
            context.verifyInteraction();
        }
    }

    @BeforeEach
    void before(PactVerificationContext context) {
        MockMvcTestTarget testTarget = new MockMvcTestTarget();
        //System.getProperties().setProperty("pact.verifier.publishResults", "true");
        testTarget.setControllers(new GetAssignmentController(
            retrieveRoleAssignmentServiceMock
        ));
        if (context != null) {
            context.setTarget(testTarget);
        }

    }

    @State({"An actor with provided id is available in role assignment service"})
    public void getActorByIdWithSuccess() throws Exception {
        setInitiMock();
    }

    private void setInitiMock() throws Exception {
        var actorId = "23486";
        List<RoleAssignment> roleAssignments
            = TestDataBuilder.buildRoleAssignmentList_Custom(Status.LIVE, actorId, "attributes_orm_orgrole.json",
                                                             RoleType.ORGANISATION, "senior-tribunal-caseworker");

        when(persistenceService.getAssignmentsByActor(anyString())).thenReturn(roleAssignments);
    }
}
