package uk.gov.hmcts.reform.roleassignment.controller.endpoints;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleConfigRole;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.getroles.RetrieveRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

@RunWith(MockitoJUnitRunner.class)
class GetAssignmentControllerTest {

    @Mock
    private transient RetrieveRoleAssignmentOrchestrator retrieveRoleAssignmentServiceMock;

    @InjectMocks
    @Spy
    private final GetAssignmentController sut = new GetAssignmentController(retrieveRoleAssignmentServiceMock);

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getListOfRoles() {
        ResponseEntity<List<RoleConfigRole>> response = sut.getListOfRoles("123e4567-e89b-42d3-a456-556642445555");
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldGetRoleAssignmentResourceWithOutBody() {
        String actorId = "123e4567-e89b-42d3-a456-556642445678";
        ResponseEntity<RoleAssignmentResource> expectedResponse = ResponseEntity.status(HttpStatus.OK).body(null);

        doReturn(expectedResponse).when(retrieveRoleAssignmentServiceMock).getAssignmentsByActor(actorId);
        ResponseEntity<RoleAssignmentResource> response = sut.retrieveRoleAssignmentsByActorId("", "", actorId);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse.getBody(), response.getBody());
        assertNull(response.getHeaders().getETag());
    }

    @Test
    void shouldGetRoleAssignmentResourceWithOutRoleAssignment() {
        String actorId = "123e4567-e89b-42d3-a456-556642445678";
        ResponseEntity<RoleAssignmentResource> expectedResponse = ResponseEntity.status(HttpStatus.OK)
            .body(new RoleAssignmentResource(null, ""));

        doReturn(expectedResponse).when(retrieveRoleAssignmentServiceMock).getAssignmentsByActor(actorId);
        ResponseEntity<RoleAssignmentResource> response = sut.retrieveRoleAssignmentsByActorId("", "", actorId);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse.getBody(), response.getBody());
        assertNull(response.getHeaders().getETag());
    }

    @Test
    void shouldGetRoleAssignmentResourceWithRoleAssignment() throws Exception {
        String actorId = "123e4567-e89b-42d3-a456-556642445678";
        ResponseEntity<RoleAssignmentResource> expectedResponse = TestDataBuilder
            .buildResourceRoleAssignmentResponse(Status.LIVE);
        doReturn(expectedResponse).when(retrieveRoleAssignmentServiceMock).getAssignmentsByActor(actorId);
        String etag = "1";
        //doReturn(etag).when(retrieveRoleAssignmentServiceMock).retrieveETag(actorId);
        ResponseEntity<RoleAssignmentResource> response = sut.retrieveRoleAssignmentsByActorId("",
                                                                                       etag, actorId);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse.getBody(), response.getBody());
        //assertNotNull(response.getHeaders().getETag());
    }
}
