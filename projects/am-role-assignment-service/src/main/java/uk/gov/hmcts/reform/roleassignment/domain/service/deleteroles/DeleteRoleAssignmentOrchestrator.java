package uk.gov.hmcts.reform.roleassignment.domain.service.deleteroles;

import com.launchdarkly.shaded.org.jetbrains.annotations.NotNull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.annotation.RequestScope;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.BadRequestException;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.MultipleQueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.PredicateValidator;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ParseRequestService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ValidationModelService;
import uk.gov.hmcts.reform.roleassignment.util.PersistenceUtil;
import uk.gov.hmcts.reform.roleassignment.util.ValidationUtil;
import uk.gov.hmcts.reform.roleassignment.versions.V1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static uk.gov.hmcts.reform.roleassignment.util.Constants.DELETE_BY_QUERY;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.NO_RECORDS;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.PROCESS;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.REFERENCE;

@Service
@RequestScope
public class DeleteRoleAssignmentOrchestrator {


    private PersistenceService persistenceService;
    private ParseRequestService parseRequestService;
    private ValidationModelService validationModelService;
    private PersistenceUtil persistenceUtil;
    private RequestEntity requestEntity;
    AssignmentRequest assignmentRequest;
    private Request request;
    @Value("${roleassignment.query.size}")
    private int defaultSize;

    public Request getRequest() {
        return request;
    }

    public RequestEntity getRequestEntity() {
        return requestEntity;
    }

    public void setRequestEntity(RequestEntity requestEntity) {
        this.requestEntity = requestEntity;
    }

    public DeleteRoleAssignmentOrchestrator(PersistenceService persistenceService,
                                            ParseRequestService parseRequestService,
                                            ValidationModelService validationModelService,
                                            PersistenceUtil persistenceUtil) {
        this.persistenceService = persistenceService;
        this.parseRequestService = parseRequestService;
        this.validationModelService = validationModelService;
        this.persistenceUtil = persistenceUtil;
    }

    @Transactional
    public ResponseEntity<Void> deleteRoleAssignmentByProcessAndReference(String process,
                                                                          String reference) {

        List<RoleAssignment> requestedRoles;

        //1. create the request Object
        try {
            if (!process.isBlank() && !reference.isBlank()) {
                request = parseRequestService.prepareDeleteRequest(process, reference, "", "");
                assignmentRequest = new AssignmentRequest(request, Collections.emptyList());
            } else {
                throw new BadRequestException(V1.Error.BAD_REQUEST_MISSING_PARAMETERS);
            }
        } catch (NullPointerException npe) {
            throw new BadRequestException(V1.Error.BAD_REQUEST_MISSING_PARAMETERS);
        }

        //2. Call persistence service to store only the request
        persistInitialRequestForDelete();

        //3. retrieve all assignment records based on actorId/process+reference
        requestedRoles = persistenceService.getAssignmentsByProcess(
            process,
            reference,
            Status.LIVE.toString()
        );
        if (requestedRoles.isEmpty()) {
            requestEntity.setStatus(Status.APPROVED.toString());
            persistenceService.updateRequest(requestEntity);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            //update the records status from Live to Delete_requested for drool to approve it.
            requestedRoles.stream().forEach(roleAssignment -> roleAssignment.setStatus(Status.DELETE_REQUESTED));
        }

        return performOtherStepsForDelete("", requestedRoles);
    }

    @Transactional
    public ResponseEntity<Void> deleteRoleAssignmentByAssignmentId(String assignmentId) {
        List<RoleAssignment> requestedRoles;

        //1. create the request Object
        if (assignmentId != null) {
            request = parseRequestService.prepareDeleteRequest("", "", "", assignmentId);
            assignmentRequest = new AssignmentRequest(request, Collections.emptyList());
        } else {
            throw new BadRequestException(V1.Error.BAD_REQUEST_MISSING_PARAMETERS);
        }

        //2. Call persistence service to store only the request
        persistInitialRequestForDelete();

        //3. retrieve all assignment records based on actorId/process+reference
        requestedRoles = persistenceService.getAssignmentById(UUID.fromString(assignmentId));
        if (requestedRoles.isEmpty()) {
            requestEntity.setStatus(Status.APPROVED.toString());
            persistenceService.updateRequest(requestEntity);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            //update the records status from Live to Delete_requested for drool to approve it.
            requestedRoles.stream().forEach(roleAssignment -> roleAssignment.setStatus(Status.DELETE_REQUESTED));
        }

        return performOtherStepsForDelete("", requestedRoles);

    }

    @NotNull
    private ResponseEntity<Void> performOtherStepsForDelete(String actorId,
                                                            List<RoleAssignment> requestedRoles) {


        //4. call validation rule
        validationByDrool(requestedRoles);

        //5. persist the  requested roles  and update status
        updateStatusAndPersist(request);

        //6. check status updated by drools and take decision
        checkAllDeleteApproved(assignmentRequest, actorId);

        if (PredicateValidator.assignmentRequestPredicate(assignmentRequest.getRequest().getStatus())
            .test(Status.REJECTED)) {
            return new ResponseEntity<>(HttpStatus.UNPROCESSABLE_ENTITY);
        } else {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
    }

    private void persistInitialRequestForDelete() {

        requestEntity = persistenceService.persistRequest(request);
        requestEntity.setHistoryEntities(new HashSet<>());
        request.setId(requestEntity.getId());
    }

    private void validationByDrool(List<RoleAssignment> requestedRoles) {

        assignmentRequest.setRequestedRoles(requestedRoles);

        //calling drools rules for validation
        validationModelService.validateRequest(assignmentRequest);


    }


    public void updateStatusAndPersist(Request request) {
        for (RoleAssignment requestedRole : assignmentRequest.getRequestedRoles()) {

            // persist history in db
            requestEntity.getHistoryEntities()
                .add(persistenceUtil.prepareHistoryEntityForPersistance(requestedRole, request));

        }
        persistenceService.persistHistoryEntities(requestEntity.getHistoryEntities());
        //Persist request to update relationship with history entities
        persistenceService.updateRequest(requestEntity);
    }

    @Transactional
    public void checkAllDeleteApproved(AssignmentRequest validatedAssignmentRequest, String actorId) {
        // decision block

        List<RoleAssignment> deleteApprovedRoles = validatedAssignmentRequest.getRequestedRoles().stream()
            .filter(role -> role.getStatus()
                .equals(Status.DELETE_APPROVED)).toList();

        if (!deleteApprovedRoles.isEmpty()
            && deleteApprovedRoles.size() == validatedAssignmentRequest.getRequestedRoles().size()) {

            //Delete existing Assignment records
            deleteLiveRecords(validatedAssignmentRequest, actorId);

            //insert status deleted in history table
            insertRequestedRole(validatedAssignmentRequest, Status.DELETED);

            // Update request status to approved
            updateRequestStatus(validatedAssignmentRequest, Status.APPROVED);

        } else {
            //Insert requested roles  into history table with status deleted-Rejected
            List<RoleAssignment> deleteApprovedRecords = validatedAssignmentRequest.getRequestedRoles().stream()
                .filter(role -> role.getStatus() == Status.DELETE_APPROVED).toList();
            validatedAssignmentRequest.setRequestedRoles(deleteApprovedRecords);
            insertRequestedRole(validatedAssignmentRequest, Status.DELETE_REJECTED);

            // Update request status to REJECTED
            updateRequestStatus(validatedAssignmentRequest, Status.REJECTED);
        }

    }

    public void deleteLiveRecords(AssignmentRequest validatedAssignmentRequest, String actorId) {
        if (!StringUtils.isEmpty(actorId)) {
            for (RoleAssignment requestedRole : validatedAssignmentRequest.getRequestedRoles()) {
                persistenceService.deleteRoleAssignmentByActorId(requestedRole.getActorId());
            }
        } else {
            for (RoleAssignment requestedRole : validatedAssignmentRequest.getRequestedRoles()) {
                persistenceService.deleteRoleAssignment(requestedRole);
            }
        }
    }


    private void insertRequestedRole(AssignmentRequest parsedAssignmentRequest, Status status) {
        for (RoleAssignment requestedRole : parsedAssignmentRequest.getRequestedRoles()) {
            requestedRole.setStatus(status);
            // persist history in db
            requestEntity.getHistoryEntities().add(persistenceUtil.prepareHistoryEntityForPersistance(
                requestedRole,
                parsedAssignmentRequest.getRequest()
            ));
        }
        persistenceService.persistHistoryEntities(requestEntity.getHistoryEntities());
        //Persist request to update relationship with history entities
        persistenceService.updateRequest(requestEntity);

    }

    private void updateRequestStatus(AssignmentRequest assignmentRequest, Status status) {
        assignmentRequest.getRequest().setStatus(status);
        requestEntity.setStatus(status.toString());
        requestEntity.setLog(assignmentRequest.getRequest().getLog());
        persistenceService.updateRequest(requestEntity);

    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> deleteRoleAssignmentsByQuery(MultipleQueryRequest multipleQueryRequest) {

        ValidationUtil.validateQueryRequests(multipleQueryRequest.getQueryRequests());

        //1. create the request Object
        if (CollectionUtils.isNotEmpty(multipleQueryRequest.getQueryRequests())) {
            request = parseRequestService.prepareDeleteRequest(PROCESS, REFERENCE,
                                                               "", "");
            request.setLog(DELETE_BY_QUERY);
            assignmentRequest = new AssignmentRequest(request, Collections.emptyList());
        } else {
            throw new BadRequestException(V1.Error.BAD_REQUEST_MISSING_PARAMETERS);

        }

        //2. Call persistence service to store only the request
        persistInitialRequestForDelete();

        //3. retrieve all assignment records by multiple query
        List<Assignment> requestedRoles = fetchRoleAssignmentsByMultipleQuery(multipleQueryRequest);
        var responseHeaders = new HttpHeaders();
        responseHeaders.add(
            "Total-Records",
            Long.toString(persistenceService.getTotalRecords())
        );

        if (requestedRoles.isEmpty()) {
            requestEntity.setStatus(Status.APPROVED.toString());
            requestEntity.setLog(NO_RECORDS);
            persistenceService.updateRequest(requestEntity);
            return new ResponseEntity<>(responseHeaders, HttpStatus.OK);
        } else {
            //update the records status from Live to Delete_requested for drool to approve it.
            requestedRoles.stream().forEach(roleAssignment -> roleAssignment.setStatus(Status.DELETE_REQUESTED));
        }

        //Perform others delete steps
        ResponseEntity<Void> responseEntity = performOtherStepsForDelete(
            "",
            (List<RoleAssignment>) (List<?>) requestedRoles
        );
        if (responseEntity.getStatusCode() == HttpStatus.NO_CONTENT) {
            return  new ResponseEntity<>(responseHeaders, HttpStatus.OK);
        }

        return responseEntity;

    }

    private List<Assignment> fetchRoleAssignmentsByMultipleQuery(MultipleQueryRequest multipleQueryRequest) {
        List<List<Assignment>> assignmentRecords = new ArrayList<>();

        ValidationUtil.validateQueryRequests(multipleQueryRequest.getQueryRequests());

        assignmentRecords.add(persistenceService.retrieveRoleAssignmentsByMultipleQueryRequest(
            multipleQueryRequest,
            0,
            0,
            null,
            null,
            false
                              )

        );
        var totalRecords = persistenceService.getTotalRecords();
        double pageNumber = 0;
        if (defaultSize > 0) {
            pageNumber = (double) totalRecords / (double) defaultSize;
        }

        for (var page = 1; page < pageNumber; page++) {
            assignmentRecords.add(persistenceService.retrieveRoleAssignmentsByMultipleQueryRequest(
                multipleQueryRequest,
                page,
                0,
                null,
                null,
                false
            ));

        }
        return assignmentRecords.stream().flatMap(Collection::stream).toList();
    }


}
