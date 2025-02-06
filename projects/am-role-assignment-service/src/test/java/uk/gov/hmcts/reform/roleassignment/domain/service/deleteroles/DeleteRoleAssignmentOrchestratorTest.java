package uk.gov.hmcts.reform.roleassignment.domain.service.deleteroles;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.BadRequestException;
import uk.gov.hmcts.reform.roleassignment.data.HistoryEntity;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.MultipleQueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ParseRequestService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ValidationModelService;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;
import uk.gov.hmcts.reform.roleassignment.util.PersistenceUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.APPROVED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.CREATED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_APPROVED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REJECTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.DELETE_REQUESTED;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.LIVE;
import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.REJECTED;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.DELETE_BY_QUERY;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.NO_RECORDS;

@RunWith(MockitoJUnitRunner.class)
class DeleteRoleAssignmentOrchestratorTest {

    @Mock
    private ParseRequestService parseRequestService = mock(ParseRequestService.class);
    @Mock
    private PersistenceService persistenceService = mock(PersistenceService.class);
    @Mock
    private ValidationModelService validationModelService = mock(ValidationModelService.class);
    @Mock
    private PersistenceUtil persistenceUtil;

    private static final String ACTOR_ID = "21334a2b-79ce-44eb-9168-2d49a744be9c";
    private static final String PROCESS = "process";
    private static final String REFERENCE = "reference";
    AssignmentRequest assignmentRequest;
    RequestEntity requestEntity;
    RoleAssignment roleAssignment;
    HistoryEntity historyEntity;


    @InjectMocks
    private DeleteRoleAssignmentOrchestrator sut = new DeleteRoleAssignmentOrchestrator(
        persistenceService,
        parseRequestService,
        validationModelService,
        persistenceUtil
    );

    @BeforeEach
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        assignmentRequest = TestDataBuilder.buildAssignmentRequest(CREATED, Status.LIVE, false);
        requestEntity = TestDataBuilder.buildRequestEntity(assignmentRequest.getRequest());
        roleAssignment = TestDataBuilder.buildRoleAssignment(Status.LIVE);
        historyEntity = TestDataBuilder.buildHistoryIntoEntity(
            assignmentRequest.getRequestedRoles().iterator().next(), requestEntity);
    }

    @Test
    @DisplayName("should get 204 when process and reference doesn't exist")
    void shouldThrowResourceNotFoundWhenProcessNotExist() {
        mockRequest();
        ResponseEntity response = sut.deleteRoleAssignmentByProcessAndReference(PROCESS, REFERENCE);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(persistenceService, times(1)).updateRequest(any(RequestEntity.class));
    }

    @Test
    @DisplayName("should get 204 when role assignment records delete  successful")
    void shouldDeleteRoleAssignmentByProcess() {

        //Set the status approved of all requested role manually for drool validation process
        setApprovedStatusByDrool();
        mockRequest();
        when(persistenceService.getAssignmentsByProcess(
            PROCESS,
            REFERENCE,
            Status.LIVE.toString()
        )).thenReturn(Collections.emptyList());
        mockHistoryEntity();

        ResponseEntity<Void> response = sut.deleteRoleAssignmentByProcessAndReference(
            PROCESS,
            REFERENCE
        );
        assertEquals(APPROVED.toString(), sut.getRequestEntity().getStatus());
        assertEquals(sut.getRequest().getId(), sut.getRequestEntity().getId());
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

    }

    @Test
    @DisplayName("should get 422 when role assignment records delete  not successful due to invalid status")
    void shouldRejectedWithDeleteRoleAssignmentByProcessWhenRolesNotEmpty() {

        //Set the status approved of all requested role manually for drool validation process
        assignmentRequest.getRequest().setLog("Assignment Request Log");
        setApprovedStatusByDrool();
        mockRequest();
        List<RoleAssignment> roleAssignmentList = TestDataBuilder
            .buildRoleAssignmentList_Custom(DELETE_APPROVED, "1234", "attributes.json",
                                            RoleType.ORGANISATION, "senior-tribunal-caseworker");
        when(persistenceService.getAssignmentsByProcess(
            PROCESS,
            REFERENCE,
            Status.LIVE.toString()
        )).thenReturn(roleAssignmentList);
        mockHistoryEntity();

        ResponseEntity<Void> response = sut.deleteRoleAssignmentByProcessAndReference(
            PROCESS,
            REFERENCE
        );
        assertNotNull(sut.getRequest().getId());
        assertEquals("Assignment Request Log", requestEntity.getLog());
        assertNotNull(response);
        assertEquals(REJECTED.toString(), sut.getRequestEntity().getStatus());
        assertEquals(sut.getRequest().getId(), sut.getRequestEntity().getId());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(1, roleAssignmentList.stream().filter(x -> x.getStatus() == DELETE_REQUESTED).count());
        assertNotNull(assignmentRequest.getRequestedRoles());
    }

    @Test
    @DisplayName("should delete records from role_assignment table for a valid Assignment Id")
    void shouldDeleteRecordsFromRoleAssignment_withAssignments() throws Exception {

        //Set the status approved of all requested role manually for drool validation process
        final String assignmentId = UUID.randomUUID().toString();
        setApprovedStatusByDrool();
        mockRequest();
        RoleAssignment assignment = TestDataBuilder.buildRoleAssignment(APPROVED);
        when(persistenceService.getAssignmentById(any())).thenReturn(List.of(assignment));
        mockHistoryEntity();
        ResponseEntity<?> response = sut.deleteRoleAssignmentByAssignmentId(assignmentId);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(DELETE_REQUESTED, assignment.getStatus());
        assertEquals(UUID.fromString("ab4e8c21-27a0-4abd-aed8-810fdce22adb"), requestEntity.getId());
        verify(persistenceService, times(1)).getAssignmentById(UUID.fromString(assignmentId));
    }

    @Test
    @DisplayName("should delete records from role_assignment table for a valid Assignment Id")
    void shouldDeleteRecordsFromRoleAssignment() {

        //Set the status approved of all requested role manually for drool validation process
        String assignmentId = UUID.randomUUID().toString();
        setApprovedStatusByDrool();
        mockRequest();
        when(persistenceService.getAssignmentById(UUID.fromString(assignmentId)))
            .thenReturn(Collections.emptyList());
        mockHistoryEntity();
        ResponseEntity<?> response = sut.deleteRoleAssignmentByAssignmentId(assignmentId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(sut.getRequestEntity().getStatus(), APPROVED.toString());
        verify(persistenceService, times(1)).getAssignmentById(UUID.fromString(assignmentId));
    }

    @Test
    @DisplayName("should delete any delete approved records that are present")
    void shouldDeleteRecordsForApprovedItems() {
        mockRequest();
        when(persistenceUtil.prepareHistoryEntityForPersistance(any(), any())).thenReturn(historyEntity);
        assignmentRequest.getRequestedRoles().forEach(roleAssignment1 -> roleAssignment1.setStatus(DELETE_APPROVED));
        mockHistoryEntity();

        //set history entity into request entity
        Set<HistoryEntity> historyEntities = new HashSet<>();
        historyEntities.add(historyEntity);
        requestEntity.setHistoryEntities(historyEntities);
        sut.setRequestEntity(requestEntity);
        sut.checkAllDeleteApproved(assignmentRequest, assignmentRequest.getRequest().getAssignerId());
        verify(persistenceService, times(2)).deleteRoleAssignmentByActorId(any());
        verify(persistenceService, times(2)).updateRequest(any(RequestEntity.class));
    }

    @Test
    @DisplayName("should delete only delete approved records and remove other records")
    void shouldDeleteRecordsOnlyForApprovedItems() {
        mockRequest();
        when(persistenceUtil.prepareHistoryEntityForPersistance(any(), any())).thenReturn(historyEntity);
        //Set 1 of 2 to delete approved status
        assignmentRequest.getRequestedRoles().iterator().next().setStatus(DELETE_APPROVED);

        mockHistoryEntity();

        //set history entity into request entity
        Set<HistoryEntity> historyEntities = new HashSet<>();
        historyEntities.add(historyEntity);
        requestEntity.setHistoryEntities(historyEntities);
        sut.setRequestEntity(requestEntity);
        sut.checkAllDeleteApproved(assignmentRequest, assignmentRequest.getRequest().getAssignerId());
        assertEquals(1, assignmentRequest.getRequestedRoles().size());
        assertEquals(REJECTED.toString(), sut.getRequestEntity().getStatus());
        assertEquals(assignmentRequest.getRequest().getLog(), sut.getRequestEntity().getLog());

    }

    @Test
    @DisplayName("should delete any delete approved records that are present even with no actorID passed")
    void shouldDeleteRecordsForApprovedItemsNoActorID() {
        mockRequest();
        when(persistenceUtil.prepareHistoryEntityForPersistance(any(), any())).thenReturn(historyEntity);
        assignmentRequest.getRequestedRoles().forEach(roleAssignment1 -> roleAssignment1.setStatus(DELETE_APPROVED));
        mockHistoryEntity();

        //set history entity into request entity
        Set<HistoryEntity> historyEntities = new HashSet<>();
        historyEntities.add(historyEntity);
        requestEntity.setHistoryEntities(historyEntities);
        sut.setRequestEntity(requestEntity);
        sut.checkAllDeleteApproved(assignmentRequest, "");
        verify(persistenceService, times(2)).deleteRoleAssignment(any());
        verify(persistenceService, times(2)).updateRequest(any(RequestEntity.class));
    }

    @Test
    @DisplayName("should throw 204 exception for a non existing Assignment id")
    void shouldThrowNotFoundForAssignmentId() {
        String assignmentId = UUID.randomUUID().toString();
        setApprovedStatusByDrool();
        mockRequest();
        when(persistenceService.getAssignmentById(UUID.fromString(assignmentId))).thenReturn(Collections.emptyList());
        mockHistoryEntity();

        ResponseEntity<?> response = sut.deleteRoleAssignmentByAssignmentId(assignmentId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(persistenceService, times(1)).updateRequest(any(RequestEntity.class));
    }

    @Test
    @DisplayName("should throw 400 exception for a no Assignment id")
    void shouldThrowBadRequestForAssignmentId() {
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.deleteRoleAssignmentByAssignmentId(null)
        );
    }

    @Test
    @DisplayName("should not delete any records if delete approved records are zero")
    void shouldNotDeleteRecordsForZeroApprovedItems() {
        mockRequest();
        when(persistenceUtil.prepareHistoryEntityForPersistance(any(), any())).thenReturn(historyEntity);
        sut.setRequestEntity(new RequestEntity());
        sut.checkAllDeleteApproved(new AssignmentRequest(new Request(), Collections.emptyList()), "actorId");
        verify(persistenceService, times(0)).deleteRoleAssignmentByActorId(any());
        verify(persistenceService, times(2)).updateRequest(any(RequestEntity.class));
    }

    @Test
    @DisplayName("should not delete any records if delete approved records don't match requested items")
    void shouldNotDeleteRecordsForNonMatchingRequestItems() {
        mockRequest();
        RoleAssignment roleAssignment = RoleAssignment.builder().status(DELETE_APPROVED).build();
        when(persistenceUtil.prepareHistoryEntityForPersistance(any(), any())).thenReturn(historyEntity);

        sut.setRequestEntity(RequestEntity.builder().historyEntities(new HashSet<>()).build());
        sut.checkAllDeleteApproved(new AssignmentRequest(
            new Request(),
            new ArrayList<>() {
                {
                    add(roleAssignment);
                    add(RoleAssignment.builder().status(DELETE_REJECTED).build());
                    add(RoleAssignment.builder().status(DELETED).build());

                }
            }
        ), "actorId");
        assertEquals(DELETE_REJECTED, roleAssignment.getStatus());
        verify(persistenceService, times(0)).deleteRoleAssignmentByActorId(any());
        verify(persistenceService, times(2)).updateRequest(any(RequestEntity.class));

    }

    @Test
    @DisplayName("should throw Unprocessable Entity 422 if any record is rejected for deletion")
    void shouldThrowUnprocessableIfRecordIsRejected() {
        //Set the status approved of all requested role manually for drool validation process
        setApprovedStatusByDrool();
        mockRequest();
        when(persistenceService.getAssignmentsByProcess(
            PROCESS,
            REFERENCE,
            Status.LIVE.toString()
        ))
            .thenReturn(new ArrayList<>() {
                {
                    add(RoleAssignment.builder().status(DELETE_APPROVED).build());
                    add(RoleAssignment.builder().status(DELETE_REJECTED).build());
                    add(RoleAssignment.builder().status(DELETED).build());
                }
            });
        mockHistoryEntity();
        ResponseEntity response = sut.deleteRoleAssignmentByProcessAndReference(PROCESS, REFERENCE);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        verify(validationModelService, times(1)).validateRequest(any(AssignmentRequest.class));
        verify(persistenceService, times(3)).updateRequest(any(RequestEntity.class));
        verify(persistenceService, times(2)).persistHistoryEntities(any());

    }

    @Test
    @DisplayName("should throw 400 when reference doesn't exist")
    void shouldThrowBadRequestWhenReferenceNotExist() {
        mockRequest();
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.deleteRoleAssignmentByProcessAndReference(PROCESS, null)
        );
    }

    @Test
    @DisplayName("should throw 400 when reference blank")
    void shouldThrowBadRequestWhenReferenceBlank() {
        mockRequest();
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.deleteRoleAssignmentByProcessAndReference(PROCESS, " ")
        );
    }

    @Test
    @DisplayName("should throw 400 when process doesn't exist")
    void shouldThrowBadRequestWhenProcessNotExist() {
        mockRequest();
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.deleteRoleAssignmentByProcessAndReference(null, REFERENCE)
        );
    }

    @Test
    @DisplayName("should throw 400 when process blank")
    void shouldThrowBadRequestWhenProcessBlank() {
        mockRequest();
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.deleteRoleAssignmentByProcessAndReference(" ", REFERENCE)
        );
    }

    @Test
    @DisplayName("should throw 422 when any record is rejected for deletion")
    void shouldThrowUnProcessExceptionByMultipleQueryRequest() throws Exception {
        //Set the status approved of all requested role manually for drool validation process
        setApprovedStatusByDrool();
        mockRequest();
        doReturn(TestDataBuilder.buildRequestedRoleCollection(LIVE)).when(persistenceService)
            .retrieveRoleAssignmentsByMultipleQueryRequest(
                any(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                anyBoolean()
        );
        when(persistenceService.getTotalRecords()).thenReturn(21L);
        ReflectionTestUtils.setField(
            sut,
            "defaultSize", 20

        );
        mockHistoryEntity();
        List<String> roleType = List.of("CASE", "ORGANISATION");

        QueryRequest queryRequest = QueryRequest.builder()
            .roleType(roleType)
            .actorId("123456")
            .build();
        MultipleQueryRequest multipleQueryRequest = MultipleQueryRequest.builder()
            .queryRequests(Collections.singletonList(queryRequest))
            .build();

        ResponseEntity<Void> response = sut.deleteRoleAssignmentsByQuery(multipleQueryRequest);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        verify(validationModelService, times(1)).validateRequest(any(AssignmentRequest.class));
        verify(persistenceService, times(3)).updateRequest(any(RequestEntity.class));
        verify(persistenceService, times(2)).persistHistoryEntities(any());

    }

    @Test
    @DisplayName("should throw Bad Request when requests empty")
    void shouldThrowBadRequestExceptionByMultipleQueryRequest() throws Exception {
        //Set the status approved of all requested role manually for drool validation process
        setApprovedStatusByDrool();
        mockRequest();
        doReturn(TestDataBuilder.buildRequestedRoleCollection(LIVE)).when(persistenceService)
            .retrieveRoleAssignmentsByMultipleQueryRequest(
                any(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                anyBoolean());
        when(persistenceService.getTotalRecords()).thenReturn(21L);
        ReflectionTestUtils.setField(
            sut,
            "defaultSize", 20

        );
        mockHistoryEntity();

        MultipleQueryRequest multipleQueryRequest = MultipleQueryRequest.builder()
            .queryRequests(Collections.emptyList())
            .build();

        Assertions.assertThrows(BadRequestException.class, () ->
            sut.deleteRoleAssignmentsByQuery(multipleQueryRequest)
        );

    }

    private void assertion() {
        verify(parseRequestService, times(1)).prepareDeleteRequest(any(), any(), any(), any());
        verify(persistenceService, times(1)).persistRequest(any(Request.class));
        verify(persistenceUtil, times(4))
            .prepareHistoryEntityForPersistance(any(RoleAssignment.class), any(Request.class));
    }

    private void mockRequest() {
        when(parseRequestService.prepareDeleteRequest(any(), any(), any(), any())).thenReturn(
            assignmentRequest.getRequest());
        when(persistenceService.persistRequest(any())).thenReturn(requestEntity);
    }

    private void setApprovedStatusByDrool() {
        for (RoleAssignment requestedRole : assignmentRequest.getRequestedRoles()) {
            requestedRole.setStatus(Status.APPROVED);
        }
        historyEntity.setStatus(DELETE_APPROVED.toString());
    }

    private void mockHistoryEntity() {
        doNothing().when(validationModelService).validateRequest(assignmentRequest);
        when(persistenceUtil.prepareHistoryEntityForPersistance(
            roleAssignment,
            assignmentRequest.getRequest()
        )).thenReturn(historyEntity);
    }

    @Test
    @DisplayName("should get 200 when role assignment records delete  successful")
    void shouldDeleteRoleAssignmentByQueryRequest() {

        //Set the status approved of all requested role manually for drool validation process
        setApprovedStatusByDrool();
        mockRequest();


        doReturn(Collections.emptyList()).when(persistenceService)
            .retrieveRoleAssignmentsByMultipleQueryRequest(
                any(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                anyBoolean()
        );
        mockHistoryEntity();

        List<String> roleType = List.of("CASE", "ORGANISATION");

        QueryRequest queryRequest = QueryRequest.builder()
            .roleType(roleType)
            .build();
        MultipleQueryRequest multipleQueryRequest = MultipleQueryRequest.builder()
            .queryRequests(Collections.singletonList(queryRequest))
            .build();

        ResponseEntity<Void> response = sut.deleteRoleAssignmentsByQuery(multipleQueryRequest);
        assertEquals(APPROVED.toString(), sut.getRequestEntity().getStatus());
        assertEquals(sut.getRequest().getId(), sut.getRequestEntity().getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().containsKey("Total-Records"));
        assertEquals(DELETE_BY_QUERY, sut.getRequest().getLog());
        assertEquals(NO_RECORDS, requestEntity.getLog());

        verify(persistenceService, times(1)).updateRequest(any(RequestEntity.class));
    }

    @Test
    @DisplayName("should throw 400 when query request empty")
    void shouldThrowBadRequestWhenQueryRequestEmpty() {
        mockRequest();
        MultipleQueryRequest multipleQueryRequest = MultipleQueryRequest.builder()
            .queryRequests(Collections.emptyList())
            .build();

        Assertions.assertThrows(BadRequestException.class, () ->
            sut.deleteRoleAssignmentsByQuery(multipleQueryRequest)
        );
    }

}
