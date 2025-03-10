package uk.gov.hmcts.reform.roleassignment.domain.service.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentRequestResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.io.IOException;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

@RunWith(MockitoJUnitRunner.class)
class PrepareResponseServiceTest {

    @InjectMocks
    PrepareResponseService prepareResponseService = new PrepareResponseService();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void prepareCreateRoleResponse() throws IOException {

        AssignmentRequest assignmentRequest = TestDataBuilder
            .buildAssignmentRequest(Status.CREATED, Status.LIVE, false);
        assignmentRequest.getRequest().setClientId("ccd");
        ResponseEntity<RoleAssignmentRequestResource> responseEntity =
            prepareResponseService
                .prepareCreateRoleResponse(assignmentRequest);
        RoleAssignmentRequestResource assignmentRequestResponse =
            responseEntity.getBody();
        assert assignmentRequestResponse != null;
        assertNull(assignmentRequestResponse.getRoleAssignmentRequest().getRequest().getClientId());
        assertNotNull(assignmentRequestResponse.getRoleAssignmentRequest().getRequest());
        assertNotNull(assignmentRequestResponse.getRoleAssignmentRequest().getRequestedRoles());
    }

    @Test
    void prepareCreateRoleResponse_Rejected() throws IOException {
        ResponseEntity<RoleAssignmentRequestResource> responseEntity =
            prepareResponseService
                .prepareCreateRoleResponse(TestDataBuilder.buildAssignmentRequest(Status.REJECTED, Status.LIVE, false));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, responseEntity.getStatusCode());
    }

    @Test
    void prepareRetrieveRoleResponse() throws Exception {
        ResponseEntity<RoleAssignmentResource> responseEntity =
            prepareResponseService
                .prepareRetrieveRoleResponse(
                    (List<RoleAssignment>) TestDataBuilder
                        .buildRequestedRoleCollection(Status.LIVE),
                    "6b36bfc6-bb21-11ea-b3de-0242ac140004"
                );
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    }
}
