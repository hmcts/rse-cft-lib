package uk.gov.hmcts.reform.roleassignment;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junitsupport.Provider;
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
import uk.gov.hmcts.reform.roleassignment.domain.service.getroles.RetrieveRoleAssignmentOrchestrator;

@ExtendWith(SpringExtension.class)
@Provider("am_roleAssignment_getRoles")
@PactBroker(scheme = "${PACT_BROKER_SCHEME:http}",
    host = "${PACT_BROKER_URL:localhost}", port = "${PACT_BROKER_PORT:9292}", consumerVersionSelectors = {
        @VersionSelector(tag = "master")})
@TestPropertySource(properties = {"roleassignment.query.size=20"})
@Import(RoleAssignmentProviderTestConfiguration.class)
@IgnoreNoPactsToVerify
public class GetRoleAssignmentProviderTest {

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
}
