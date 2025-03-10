package uk.gov.hmcts.reform.roleassignment.domain.service.getroles;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.BadRequestException;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.UnprocessableEntityException;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleConfigRole;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PrepareResponseService;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
class RetrieveRoleAssignmentOrchestratorTest {

    @Mock
    private PersistenceService persistenceService = mock(PersistenceService.class);

    @Mock
    private PrepareResponseService prepareResponseService = mock(PrepareResponseService.class);

    private static final String ROLE_TYPE = "CASE";

    @InjectMocks
    private RetrieveRoleAssignmentOrchestrator sut = new RetrieveRoleAssignmentOrchestrator(
        persistenceService,
        prepareResponseService
    );

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getRoleAssignment_shouldGetAssignmentsByActor() throws Exception {

        List<RoleAssignment> roleAssignments
            = (List<RoleAssignment>) TestDataBuilder.buildRequestedRoleCollection(Status.LIVE);
        String actorId = "123e4567-e89b-42d3-a456-556642445678";
        ResponseEntity<RoleAssignmentResource> roles = TestDataBuilder.buildResourceRoleAssignmentResponse(Status.LIVE);
        when(persistenceService.getAssignmentsByActor(actorId)).thenReturn(roleAssignments);
        when(prepareResponseService.prepareRetrieveRoleResponse(roleAssignments, actorId)).thenReturn(
            roles);

        ResponseEntity<RoleAssignmentResource> response = sut.getAssignmentsByActor(actorId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(persistenceService, times(1)).getAssignmentsByActor(any(String.class));
        verify(prepareResponseService, times(1))
            .prepareRetrieveRoleResponse(any(), any(String.class));
    }

    @Test
    void getRoleAssignment_shouldThrowBadRequestWhenActorIsEmpty() throws Exception {

        List<RoleAssignment> roleAssignments
            = (List<RoleAssignment>) TestDataBuilder.buildRequestedRoleCollection(Status.LIVE);
        String actorId = "";
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.getAssignmentsByActor(actorId)
        );
    }

    @Test
    void getRoleAssignment_shouldThrowUnprocessableException() {
        doThrow(UnprocessableEntityException.class).when(persistenceService).getAssignmentsByActor(anyString());
        String actorId = "123e4567-e89b-42d3-a456-556642445678";
        Assertions.assertThrows(UnprocessableEntityException.class, () ->
            sut.getAssignmentsByActor(actorId)
        );
    }


    @Test
    void getRoleAssignment_shouldThrowBadRequestWhenActorIsNotValid() throws Exception {

        List<RoleAssignment> roleAssignments
            = (List<RoleAssignment>) TestDataBuilder.buildRequestedRoleCollection(Status.LIVE);
        String actorId = "123e4567-e89b-42d3-a456-^&%$£%";
        Assertions.assertThrows(BadRequestException.class, () ->
            sut.getAssignmentsByActor(actorId)
        );
    }

    @Test
    void getRoleAssignment_shouldNotThrowResourceNotFoundWhenActorIsNotAvailable() {
        List<RoleAssignment> roleAssignments = new ArrayList<>();
        String actorId = "123e4567-e89b-42d3-a456-556642445678";

        when(persistenceService.getAssignmentsByActor(actorId)).thenReturn(roleAssignments);
        ResponseEntity<RoleAssignmentResource> responseEntity = ResponseEntity.status(HttpStatus.OK).body(
            new RoleAssignmentResource(List.of(), actorId));
        when(prepareResponseService.prepareRetrieveRoleResponse(anyList(), any())).thenReturn(responseEntity);

        ResponseEntity<RoleAssignmentResource> response = sut.getAssignmentsByActor(actorId);
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getRoleAssignmentResponse());
    }

    @Test
    void getListOfRoles() {
        List<RoleConfigRole> roles = sut.getListOfRoles();
        assertNotNull(roles);
        assertTrue(roles.size() > 2);
    }
}
