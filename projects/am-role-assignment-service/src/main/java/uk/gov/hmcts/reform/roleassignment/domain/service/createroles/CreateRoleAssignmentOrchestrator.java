package uk.gov.hmcts.reform.roleassignment.domain.service.createroles;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentRequestResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RequestType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ParseRequestService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PrepareResponseService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ValidationModelService;
import uk.gov.hmcts.reform.roleassignment.util.PersistenceUtil;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status.APPROVED;

@Slf4j
@Service
@RequestScope
public class CreateRoleAssignmentOrchestrator {

    private ParseRequestService parseRequestService;
    private PrepareResponseService prepareResponseService;
    private CreateRoleAssignmentService createRoleAssignmentService;
    private PersistenceService persistenceService;
    private ValidationModelService validationModelService;
    private PersistenceUtil persistenceUtil;

    Request request;
    RequestEntity requestEntity;


    public CreateRoleAssignmentOrchestrator(ParseRequestService parseRequestService,
                                            PrepareResponseService prepareResponseService,
                                            PersistenceService persistenceService,
                                            ValidationModelService validationModelService,
                                            PersistenceUtil persistenceUtil) {
        this.parseRequestService = parseRequestService;
        this.prepareResponseService = prepareResponseService;
        this.persistenceService = persistenceService;

        this.validationModelService = validationModelService;
        this.persistenceUtil = persistenceUtil;
    }

    public ResponseEntity<RoleAssignmentRequestResource> createRoleAssignment(AssignmentRequest roleAssignmentRequest)
        throws ParseException {
        try {
            AssignmentRequest existingAssignmentRequest;
            createRoleAssignmentService = new CreateRoleAssignmentService(
                parseRequestService,
                persistenceService,
                validationModelService,
                persistenceUtil,
                prepareResponseService
            );

            //1. call parse request service

            var parsedAssignmentRequest = parseRequestService
                .parseRequest(roleAssignmentRequest, RequestType.CREATE);

            //2. Call persistence service to store only the request
            requestEntity = createRoleAssignmentService.persistInitialRequest(parsedAssignmentRequest.getRequest());
            requestEntity.setHistoryEntities(new HashSet<>());
            request = parsedAssignmentRequest.getRequest();
            request.setId(requestEntity.getId());
            createRoleAssignmentService.setRequestEntity(requestEntity);
            createRoleAssignmentService.setIncomingRequest(request);

            //Check replace existing true/false
            if (request.isReplaceExisting()) {

                //retrieve existing assignments and prepared temp request
                existingAssignmentRequest = createRoleAssignmentService
                    .retrieveExistingAssignments(parsedAssignmentRequest);

                // return 201 when there is no existing records in db and incoming request also have
                // empty requested roles.
                if (isExistingAndIncomingRecordsEmpty(existingAssignmentRequest, parsedAssignmentRequest)) {
                    return ResponseEntity.status(HttpStatus.CREATED).body(new RoleAssignmentRequestResource(
                        parsedAssignmentRequest));
                }

                // compare identical existing and incoming requested roles based on some attributes
                try {
                    if (createRoleAssignmentService.hasAssignmentsUpdated(
                        existingAssignmentRequest,
                        parsedAssignmentRequest
                    )) {
                        identifyAssignmentsToBeUpdated(existingAssignmentRequest, parsedAssignmentRequest);

                    } else {
                        createRoleAssignmentService.duplicateRequest(
                            existingAssignmentRequest,
                            parsedAssignmentRequest
                        );
                    }

                    //8. Call the persistence to copy assignment records to RoleAssignmentLive table
                    if (!createRoleAssignmentService.needToCreateRoleAssignments.isEmpty()
                        && !createRoleAssignmentService.needToRetainRoleAssignments.isEmpty()) {
                        parsedAssignmentRequest.getRequestedRoles()
                            .addAll(createRoleAssignmentService.needToRetainRoleAssignments);
                    } else if (!createRoleAssignmentService.needToRetainRoleAssignments.isEmpty()) {
                        Set<RoleAssignment> assignments =
                            new HashSet<>(createRoleAssignmentService.needToRetainRoleAssignments);
                        parsedAssignmentRequest.setRequestedRoles(assignments);
                    }
                } catch (InvocationTargetException | IllegalAccessException e) {
                    // Don't throw the exception, as we need to build the response as Http:201
                    log.error("context", e);
                }

            } else {
                //Save requested role in history table with CREATED and Approved Status
                createRoleAssignmentService.createNewAssignmentRecords(parsedAssignmentRequest);
                createRoleAssignmentService.checkAllApproved(parsedAssignmentRequest);

            }

            ResponseEntity<RoleAssignmentRequestResource> result = prepareResponseService
                .prepareCreateRoleResponse(parsedAssignmentRequest);

            parseRequestService.removeCorrelationLog();
            return result;
        } finally {
            flushGlobalVariables();

        }

    }

    private void flushGlobalVariables() {
        if (createRoleAssignmentService.needToDeleteRoleAssignments != null) {
            createRoleAssignmentService.needToDeleteRoleAssignments.clear();
        }
        if (createRoleAssignmentService.needToCreateRoleAssignments != null) {
            createRoleAssignmentService.needToCreateRoleAssignments.clear();
        }
        if (createRoleAssignmentService.needToRetainRoleAssignments != null) {
            createRoleAssignmentService.needToRetainRoleAssignments.clear();
        }
        if (!createRoleAssignmentService.emptyUUIds.isEmpty()) {
            createRoleAssignmentService.emptyUUIds.clear();

        }
    }

    private boolean isExistingAndIncomingRecordsEmpty(AssignmentRequest existingAssignmentRequest,
                                         AssignmentRequest parsedAssignmentRequest) {
        if (existingAssignmentRequest.getRequestedRoles().isEmpty()
            && parsedAssignmentRequest.getRequestedRoles().isEmpty()) {
            request.setStatus(APPROVED);
            request.setLog("Request has been approved");
            requestEntity.setStatus(Status.APPROVED.toString());
            requestEntity.setLog(request.getLog());
            persistenceService.updateRequest(requestEntity);
            return true;
        }
        return false;
    }

    private void identifyAssignmentsToBeUpdated(AssignmentRequest existingAssignmentRequest,
                                                AssignmentRequest parsedAssignmentRequest)
        throws IllegalAccessException, InvocationTargetException {

        //update the existingAssignmentRequest with Only need to be removed record
        if (!createRoleAssignmentService.needToDeleteRoleAssignments.isEmpty()) {
            createRoleAssignmentService.updateExistingAssignments(
                existingAssignmentRequest);
        }

        //update the parsedAssignmentRequest with Only new record
        if (!createRoleAssignmentService.needToCreateRoleAssignments.isEmpty()) {
            createRoleAssignmentService.updateNewAssignments(
                existingAssignmentRequest,
                parsedAssignmentRequest
            );

        } else {
            parsedAssignmentRequest.setRequestedRoles(Collections.emptyList());
        }

        //Checking all assignments has DELETE_APPROVED status to create new entries of assignment records
        createRoleAssignmentService.checkAllDeleteApproved(existingAssignmentRequest, parsedAssignmentRequest);


    }
}
