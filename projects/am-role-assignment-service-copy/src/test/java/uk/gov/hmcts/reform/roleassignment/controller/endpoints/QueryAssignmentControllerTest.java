package uk.gov.hmcts.reform.roleassignment.controller.endpoints;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.MultipleQueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.queryroles.QueryRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
class QueryAssignmentControllerTest {

    private final QueryRoleAssignmentOrchestrator queryRoleAssignmentOrchestrator =
        mock(QueryRoleAssignmentOrchestrator.class);

    private static final String ROLE_TYPE = "CASE";


    @InjectMocks
    private final QueryAssignmentController sut = new QueryAssignmentController(queryRoleAssignmentOrchestrator);

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void shouldGetIdLdDemo() {
        ResponseEntity<Object> response = sut.getIdLdDemo("123e4567-e89b-42d3-a456-556642445555");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertEquals("Launch Darkly flag check is successful for the endpoint", response.getBody().toString());

    }

    @Test
    void shouldPostRoleAssignmentQueryByRequest() throws Exception {
        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );
        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .build();
        ResponseEntity<RoleAssignmentResource> expectedResponse
            = TestDataBuilder.buildResourceRoleAssignmentResponse(Status.LIVE);
        doReturn(expectedResponse).when(queryRoleAssignmentOrchestrator)
            .retrieveRoleAssignmentsByQueryRequest(queryRequest, 0, 20, "id", "desc", true);
        ResponseEntity<RoleAssignmentResource> response = sut
            .retrieveRoleAssignmentsByQueryRequest("", 0, 20, "id", "desc", queryRequest, true);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse.getBody(), response.getBody());
    }

    @Test
    void shouldReturnBadRequestForInvalidRequestBody() {
        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .build();
        ResponseEntity<RoleAssignmentResource> expectedResponse = ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        doReturn(expectedResponse).when(queryRoleAssignmentOrchestrator)
            .retrieveRoleAssignmentsByQueryRequest(queryRequest, 0, 20, "roleType", "desc", true);

        ResponseEntity<RoleAssignmentResource> response = sut.retrieveRoleAssignmentsByQueryRequest(
            "",
            0,
            20,
            "roleType",
            "desc",
            queryRequest,
            true
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

    }

    @Test
    void shouldReturnEmptyResultIfNoRecordsFound() {
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");

        QueryRequest queryRequest = QueryRequest.builder()
            .roleType(roleType)
            .build();
        ResponseEntity<RoleAssignmentResource> expectedResponse = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        doReturn(expectedResponse).when(queryRoleAssignmentOrchestrator)
            .retrieveRoleAssignmentsByQueryRequest(queryRequest, 0, 20, "id", "asc", true);

        ResponseEntity<RoleAssignmentResource> response = sut.retrieveRoleAssignmentsByQueryRequest(
            "",
            0,
            20,
            "id",
            "asc",
            queryRequest,
            true
        );
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

    }

    @Test
    void shouldPostRoleAssignmentQueryByRequests() throws Exception {
        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );


        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .build();
        MultipleQueryRequest multipleQueryRequest =  MultipleQueryRequest.builder()
            .queryRequests(Collections.singletonList(queryRequest))
            .build();

        ResponseEntity<RoleAssignmentResource> expectedResponse
            = TestDataBuilder.buildResourceRoleAssignmentResponse(Status.LIVE);
        doReturn(expectedResponse).when(queryRoleAssignmentOrchestrator)
            .retrieveRoleAssignmentsByMultipleQueryRequest(multipleQueryRequest, 0, 20, "id", "desc", true);
        ResponseEntity<RoleAssignmentResource> response = sut
            .retrieveRoleAssignmentsByQueryRequestV2("", 0, 20, "id", "desc", multipleQueryRequest, true);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse.getBody(), response.getBody());
    }

    @Test
    void shouldReturnBadRequestForInvalidRequestForV2() {
        List<String> actorId = Arrays.asList(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );

        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(actorId)
            .build();
        MultipleQueryRequest multipleQueryRequest =  MultipleQueryRequest.builder()
            .queryRequests(Collections.singletonList(queryRequest))
            .build();
        ResponseEntity<RoleAssignmentResource> expectedResponse = ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        doReturn(expectedResponse).when(queryRoleAssignmentOrchestrator)
            .retrieveRoleAssignmentsByMultipleQueryRequest(multipleQueryRequest, 0, 20, "roleType", "desc", true);

        ResponseEntity<RoleAssignmentResource> response = sut.retrieveRoleAssignmentsByQueryRequestV2(
            "",
            0,
            20,
            "roleType",
            "desc",
            multipleQueryRequest,
            true
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

    }

    @Test
    void shouldReturnEmptyResultIfNoRecordsFoundForV2() {
        List<String> roleType = Arrays.asList("CASE", "ORGANISATION");

        QueryRequest queryRequest = QueryRequest.builder()
            .roleType(roleType)
            .build();
        MultipleQueryRequest multipleQueryRequest =  MultipleQueryRequest.builder()
            .queryRequests(Collections.singletonList(queryRequest))
            .build();
        ResponseEntity<RoleAssignmentResource> expectedResponse = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        doReturn(expectedResponse).when(queryRoleAssignmentOrchestrator)
            .retrieveRoleAssignmentsByMultipleQueryRequest(multipleQueryRequest, 0, 20, "id", "asc", true);

        ResponseEntity<RoleAssignmentResource> response = sut.retrieveRoleAssignmentsByQueryRequestV2(
            "",
            0,
            20,
            "id",
            "asc",
            multipleQueryRequest,
            true
        );
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

    }


}
