package uk.gov.hmcts.reform.roleassignment.domain.service.createroles;

import com.launchdarkly.shaded.org.jetbrains.annotations.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.roleassignment.data.HistoryEntity;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentRequestResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentSubset;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ParseRequestService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PrepareResponseService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ValidationModelService;
import uk.gov.hmcts.reform.roleassignment.util.CreatedTimeComparator;
import uk.gov.hmcts.reform.roleassignment.util.JacksonUtils;
import uk.gov.hmcts.reform.roleassignment.util.PersistenceUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Slf4j
public class CreateRoleAssignmentService {

    private ParseRequestService parseRequestService;
    private PersistenceService persistenceService;
    private ValidationModelService validationModelService;
    private PersistenceUtil persistenceUtil;
    private PrepareResponseService prepareResponseService;

    private RequestEntity requestEntity;
    private Request incomingRequest;
    List<UUID> emptyUUIds = new ArrayList<>();
    CreatedTimeComparator createdTimeComparator;
    Map<UUID, RoleAssignmentSubset> needToDeleteRoleAssignments = new HashMap<>();
    Set<RoleAssignmentSubset> needToCreateRoleAssignments = new HashSet<>();
    Set<RoleAssignment> needToRetainRoleAssignments;
    private static final String REQUEST_REJECTION_MSG = "Request has been rejected due to following assignment Ids :";
    private static final String REQUEST_APPROVAL_MSG = "Request has been approved";

    public CreateRoleAssignmentService(ParseRequestService parseRequestService,
                                       PersistenceService persistenceService,
                                       ValidationModelService validationModelService,
                                       PersistenceUtil persistenceUtil,
                                       PrepareResponseService prepareResponseService) {
        this.parseRequestService = parseRequestService;
        this.persistenceService = persistenceService;
        this.validationModelService = validationModelService;
        this.persistenceUtil = persistenceUtil;
        this.prepareResponseService = prepareResponseService;

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAllDeleteApproved(AssignmentRequest existingAssignmentRequest,
                                       AssignmentRequest parsedAssignmentRequest) {
        // decision block
        if (MapUtils.isNotEmpty(needToDeleteRoleAssignments)) {
            List<RoleAssignment> deleteApprovedAssignments = existingAssignmentRequest.getRequestedRoles().stream()
                .filter(role -> role.getStatus().equals(
                    Status.DELETE_APPROVED)).toList();


            if (deleteApprovedAssignments.size() == existingAssignmentRequest.getRequestedRoles().size()) {

                //Create New Assignment records
                if (CollectionUtils.isNotEmpty(needToCreateRoleAssignments)) {
                    createNewAssignmentRecords(parsedAssignmentRequest);


                    // decision block
                    List<RoleAssignment> createApprovedAssignments = parsedAssignmentRequest
                        .getRequestedRoles().stream()
                        .filter(role -> role.getStatus().equals(
                            Status.APPROVED))
                        .toList();

                    if (createApprovedAssignments.size() == parsedAssignmentRequest.getRequestedRoles().size()) {
                        executeReplaceRequest(existingAssignmentRequest, parsedAssignmentRequest);
                    } else {
                        List<UUID> rejectedAssignmentIds = parsedAssignmentRequest.getRequestedRoles().stream()
                            .filter(role -> role.getStatus().equals(
                                Status.REJECTED)).map(RoleAssignment::getId).toList();
                        rejectDeleteRequest(existingAssignmentRequest, rejectedAssignmentIds, parsedAssignmentRequest);

                    }
                } else {
                    //It will delete existing records from db.
                    deleteExistingRecordsWhenNeedToCreateEmpty(
                        existingAssignmentRequest,
                        parsedAssignmentRequest.getRequest()
                    );
                }

            } else {
                List<UUID> rejectedAssignmentIds = existingAssignmentRequest.getRequestedRoles().stream()
                    .filter(role -> role.getStatus().equals(
                        Status.DELETE_REJECTED)).map(RoleAssignment::getId).toList();


                rejectDeleteRequest(existingAssignmentRequest, rejectedAssignmentIds, parsedAssignmentRequest);

            }
        } else {
            //Save requested role in history table with CREATED and Approved Status
            createNewAssignmentRecords(parsedAssignmentRequest);
            checkAllApproved(parsedAssignmentRequest);
        }


    }

    void rejectDeleteRequest(AssignmentRequest existingAssignmentRequest,
                                     List<UUID> rejectedAssignmentIds,
                                     AssignmentRequest parsedAssignmentRequest) {

        var request = parsedAssignmentRequest.getRequest();
        //Insert existingAssignmentRequest.getRequestedRoles() records into history table with status deleted-Rejected

        insertRequestedRole(existingAssignmentRequest, Status.DELETE_REJECTED, rejectedAssignmentIds);


        // Insert parsedAssignmentRequest.getRequestedRoles() records into history table with status REJECTED
        insertRequestedRole(parsedAssignmentRequest, Status.REJECTED, rejectedAssignmentIds);
        // Update request status to REJECTED
        request.setStatus(Status.REJECTED);
        requestEntity.setStatus(Status.REJECTED.toString());
        if (CollectionUtils.isNotEmpty(rejectedAssignmentIds)) {
            requestEntity.setLog(REQUEST_REJECTION_MSG + rejectedAssignmentIds);
            request.setLog(REQUEST_REJECTION_MSG + rejectedAssignmentIds);
        }

        persistenceService.updateRequest(requestEntity);

    }


    public void executeReplaceRequest(AssignmentRequest existingAssignmentRequest,
                                      AssignmentRequest parsedAssignmentRequest) {
        //delete existingAssignmentRequest.getRequestedRoles() records from live table--Hard delete
        deleteRecords(existingAssignmentRequest);


        // Insert parsedAssignmentRequest.getRequestedRoles() records into live table
        moveHistoryRecordsToLiveTable(requestEntity);

        // Insert parsedAssignmentRequest.getRequestedRoles() records into history table with status LIVE
        insertRequestedRole(parsedAssignmentRequest, Status.LIVE, emptyUUIds);

        // Update request status to approved
        updateRequestStatus(parsedAssignmentRequest.getRequest());
    }

    private void insertExistingRecordsAfterDroolValidation(AssignmentRequest existingAssignmentRequest) {
        for (RoleAssignment requestedAssignment : existingAssignmentRequest.getRequestedRoles()) {
            // persist history in db
            requestEntity.getHistoryEntities().add(persistenceUtil.prepareHistoryEntityForPersistance(
                requestedAssignment,
                incomingRequest
            ));
        }
        persistenceService.persistHistoryEntities(requestEntity.getHistoryEntities());
        //Persist request to update relationship with history entities
        persistenceService.updateRequest(requestEntity);
    }

    //Create New Assignment Records
    public void createNewAssignmentRecords(AssignmentRequest parsedAssignmentRequest) {
        //Save new requested role in history table with CREATE_REQUESTED Status

        insertRequestedRole(parsedAssignmentRequest, Status.CREATE_REQUESTED, emptyUUIds);

        validationModelService.validateRequest(parsedAssignmentRequest);

        //Save requested role in history table with APPROVED/REJECTED Status
        for (RoleAssignment requestedAssignment : parsedAssignmentRequest.getRequestedRoles()) {
            requestEntity.getHistoryEntities().add(persistenceUtil.prepareHistoryEntityForPersistance(
                requestedAssignment,
                parsedAssignmentRequest.getRequest()
            ));
        }
        persistenceService.persistHistoryEntities(requestEntity.getHistoryEntities());
        //Persist request to update relationship with history entities
        persistenceService.updateRequest(requestEntity);

    }

    private void moveHistoryRecordsToLiveTable(RequestEntity requestEntity) {
        List<HistoryEntity> historyEntities = requestEntity.getHistoryEntities()
            .stream()
            .filter(entity -> entity.getStatus().equals(
                Status.APPROVED.toString()))
            .toList();

        List<RoleAssignment> roleAssignments = historyEntities.stream().map(entity -> persistenceUtil
            .convertHistoryEntityToRoleAssignment(
                entity)).toList();
        for (RoleAssignment requestedAssignment : roleAssignments) {
            requestedAssignment.setStatus(Status.LIVE);
        }
        persistenceService.persistRoleAssignments(roleAssignments);
    }


    public RequestEntity persistInitialRequest(Request request) {

        return persistenceService.persistRequest(request);
    }

    private void deleteLiveAssignments(Collection<RoleAssignment> existingAssignments) {

        for (RoleAssignment requestedRole : existingAssignments) {
            persistenceService.deleteRoleAssignment(requestedRole);

        }

    }

    void insertRequestedRole(AssignmentRequest assignmentRequest,
                                     Status status,
                                     List<UUID> rejectedAssignmentIds) {

        List<HistoryEntity> historyEntityList = new ArrayList<>();
        for (RoleAssignment requestedAssignment : assignmentRequest.getRequestedRoles()) {
            if (CollectionUtils.isNotEmpty(rejectedAssignmentIds)
                && (status.equals(Status.REJECTED) || status.equals(Status.DELETE_REJECTED))
                &&
                (requestedAssignment.getStatus().equals(Status.APPROVED)
                    || requestedAssignment.getStatus().equals(Status.CREATE_REQUESTED)
                    || requestedAssignment.getStatus().equals(Status.DELETE_APPROVED))) {
                requestedAssignment.setLog(
                    "Requested Role has been rejected due to following new/existing assignment Ids :"
                        + rejectedAssignmentIds);
            }
            if (requestedAssignment.getStatus() == Status.APPROVED
                || requestedAssignment.getStatus() == Status.DELETE_APPROVED || requestedAssignment.getStatus().equals(
                Status.CREATE_REQUESTED)) {
                requestedAssignment.setStatus(status);
                if (requestedAssignment.getStatus().equals(
                    Status.CREATE_REQUESTED)) {
                    requestedAssignment.setLog("Create requested with replace: "
                                                   + assignmentRequest.getRequest().isReplaceExisting());
                }
                var entity = persistenceUtil.prepareHistoryEntityForPersistance(
                    requestedAssignment,
                    assignmentRequest.getRequest()
                );
                historyEntityList.add(entity);
                requestedAssignment.setId(entity.getId());
            }

        }
        persistenceService.persistHistoryEntities(historyEntityList);
        //Persist request to update relationship with history entities
        persistenceService.updateRequest(requestEntity);

    }



    public boolean hasAssignmentsUpdated(AssignmentRequest existingAssignmentRequest,
                                         AssignmentRequest parsedAssignmentRequest)
        throws InvocationTargetException, IllegalAccessException {

        needToRetainRoleAssignments = new HashSet<>();
        // convert existing assignment records into role assignment subset
        Map<UUID, RoleAssignmentSubset> existingRecords = JacksonUtils.convertExistingRolesIntoSubSet(
            existingAssignmentRequest);

        //to check if authorisation is empty
        parsedAssignmentRequest.getRequestedRoles().forEach(roleAssignment -> {
            if (CollectionUtils.isEmpty(roleAssignment.getAuthorisations())) {
                roleAssignment.setAuthorisations(null);
            }

        });


        // convert incoming assignment records into role assignment subset
        Set<RoleAssignmentSubset> incomingRecords = JacksonUtils.convertRequestedRolesIntoSubSet(
            parsedAssignmentRequest);

        //find common Role Assignment Map
        Map<UUID, RoleAssignmentSubset> commonRecords = findCommonRoleAssignments(existingRecords, incomingRecords);


        // find to create new assignment records or delete existing records.
        identifyRoleAssignments(
            existingRecords,
            incomingRecords,
            commonRecords
        );

        return MapUtils.isNotEmpty(needToDeleteRoleAssignments)
            || CollectionUtils.isNotEmpty(needToCreateRoleAssignments);
    }

    private Map<UUID, RoleAssignmentSubset> findCommonRoleAssignments(Map<UUID,
        RoleAssignmentSubset> existingRecords, Set<RoleAssignmentSubset> incomingRecords) {
        Map<UUID, RoleAssignmentSubset> commonRoleAssignments = new HashMap<>();
        existingRecords.forEach((key, value) -> {
            if (incomingRecords.contains(value)) {
                commonRoleAssignments.put(key, value);
            }
        });

        return commonRoleAssignments;
    }

    void identifyRoleAssignments(Map<UUID, RoleAssignmentSubset> existingRecords,
                                 Set<RoleAssignmentSubset> incomingRecords,
                                 Map<UUID, RoleAssignmentSubset> commonRecords) {
        // initialize  needToCreateRoleAssignment & needToDeleteRoleAssignment
        needToCreateRoleAssignments = new HashSet<>();
        needToDeleteRoleAssignments = new HashMap<>();


        if (MapUtils.isNotEmpty(commonRecords) && CollectionUtils.isNotEmpty(incomingRecords)
            && MapUtils.isNotEmpty(existingRecords)) {
            needToDeleteRoleAssignments =
                existingRecords.entrySet().stream()
                    .filter(roleAssignmentSubsetEntry ->
                                !(roleAssignmentSubsetEntry.getValue()
                                    .equals(commonRecords.get(
                                        roleAssignmentSubsetEntry
                                            .getKey()))))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            needToCreateRoleAssignments = findCreateRoleAssignments(incomingRecords, commonRecords);

        } else if (MapUtils.isEmpty(commonRecords) && CollectionUtils.isNotEmpty(incomingRecords)
            && (MapUtils.isNotEmpty(existingRecords))) {
            needToCreateRoleAssignments = incomingRecords;
            needToDeleteRoleAssignments = existingRecords;
        } else if (MapUtils.isEmpty(commonRecords) && CollectionUtils.isNotEmpty(incomingRecords)
            && (MapUtils.isEmpty(existingRecords))) {
            needToCreateRoleAssignments = incomingRecords;
        } else if (MapUtils.isEmpty(commonRecords) && CollectionUtils.isEmpty(incomingRecords)
            && (MapUtils.isNotEmpty(existingRecords))) {
            needToDeleteRoleAssignments = existingRecords;
        }
    }

    private Set<RoleAssignmentSubset> findCreateRoleAssignments(Set<RoleAssignmentSubset> incomingRecords,
                                                                Map<UUID, RoleAssignmentSubset> commonRecords) {

        Set<RoleAssignmentSubset> needToCreateRoleAssignment = new HashSet<>();
        Set<RoleAssignmentSubset> commonRecordsSet = new HashSet<>(commonRecords.values());

        incomingRecords.forEach(key -> {
            if (!commonRecordsSet.contains(key)) {
                needToCreateRoleAssignment.add(key);
            }
        });

        return needToCreateRoleAssignment;

    }

    private void deleteExistingRecordsWhenNeedToCreateEmpty(AssignmentRequest existingAssignmentRequest,
                                                            Request incomingRequest) {
        deleteRecords(existingAssignmentRequest);
        // Update request status to approved
        updateRequestStatus(incomingRequest);
    }

    private void updateRequestStatus(Request request) {
        request.setStatus(Status.APPROVED);
        //to fix the FT
        request.setLog(REQUEST_APPROVAL_MSG);
        requestEntity.setStatus(Status.APPROVED.toString());
        requestEntity.setLog(request.getLog());


        persistenceService.updateRequest(requestEntity);
    }

    private void deleteRecords(AssignmentRequest existingAssignmentRequest) {
        //delete existingAssignmentRequest.getRequestedRoles() records from live table--Hard delete
        deleteLiveAssignments(existingAssignmentRequest.getRequestedRoles());

        //Insert existingAssignmentRequest.getRequestedRoles() records into history table with status deleted-soft
        // delete
        insertRequestedRole(existingAssignmentRequest, Status.DELETED, emptyUUIds);
    }

    public ResponseEntity<RoleAssignmentRequestResource> duplicateRequest(AssignmentRequest existingAssignmentRequest,
                                                                          AssignmentRequest parsedAssignmentRequest) {
        parsedAssignmentRequest.getRequest().setStatus(Status.APPROVED);
        requestEntity.setStatus(Status.APPROVED.toString());
        requestEntity.setLog(
            "Duplicate Request: Requested Assignments are already live.");
        parsedAssignmentRequest.getRequest().setLog(
            "Duplicate Request: Requested Assignments are already live.");

        persistenceService.updateRequest(requestEntity);

        //replace new assignments with details of existing and return 201
        parsedAssignmentRequest.setRequestedRoles(existingAssignmentRequest.getRequestedRoles());


        ResponseEntity<RoleAssignmentRequestResource> result = prepareResponseService.prepareCreateRoleResponse(
            parsedAssignmentRequest);
        parseRequestService.removeCorrelationLog();
        return result;
    }

    public void updateNewAssignments(AssignmentRequest existingAssignmentRequest,
                                     AssignmentRequest parsedAssignmentRequest)
        throws IllegalAccessException, InvocationTargetException {

        List<RoleAssignment> newRoleAssignments = new ArrayList<>();

        for (RoleAssignment roleAssignment : parsedAssignmentRequest.getRequestedRoles()) {
            var roleAssignmentSubset = RoleAssignmentSubset.builder().build();
            BeanUtils.copyProperties(roleAssignmentSubset, roleAssignment);

            if (needToCreateRoleAssignments.contains(roleAssignmentSubset)) {
                newRoleAssignments.add(roleAssignment);
            }
        }
        newRoleAssignments.sort(createdTimeComparator);


        //replace parsedAssignmentRequest with new role assignments that need to be created
        parsedAssignmentRequest.setRequestedRoles(newRoleAssignments);

        if (MapUtils.isEmpty(needToDeleteRoleAssignments)
            && existingAssignmentRequest != null
            && CollectionUtils.isNotEmpty(existingAssignmentRequest.getRequestedRoles())) {
            needToRetainRoleAssignments.addAll(existingAssignmentRequest.getRequestedRoles());

        }
    }

    public void updateExistingAssignments(AssignmentRequest existingAssignmentRequest) {

        List<RoleAssignment> roleAssignmentList = existingAssignmentRequest.getRequestedRoles().stream().filter(
            e -> needToDeleteRoleAssignments.containsKey(
                e.getId())).toList();

        needToRetainRoleAssignments = existingAssignmentRequest.getRequestedRoles().stream()
            .filter(e -> !roleAssignmentList.contains(e)).collect(Collectors.toSet());

        existingAssignmentRequest.setRequestedRoles(roleAssignmentList);

        //update the records status from Live to Delete_requested for drool to approve it.
        existingAssignmentRequest.getRequestedRoles().forEach(roleAssignment -> roleAssignment
            .setStatus(Status.DELETE_REQUESTED));
        //validation
        evaluateDeleteAssignments(existingAssignmentRequest);
    }

    @NotNull
    public AssignmentRequest retrieveExistingAssignments(AssignmentRequest parsedAssignmentRequest) {

        var request = parsedAssignmentRequest.getRequest();
        List<RoleAssignment> existingAssignments = new ArrayList<>(persistenceService.getAssignmentsByProcess(
            request.getProcess(),
            request.getReference(),
            Status.LIVE.toString()
        ));

        createdTimeComparator = new CreatedTimeComparator();
        existingAssignments.sort(createdTimeComparator);
        //create a new existing assignment request for delete records
        return new AssignmentRequest(parsedAssignmentRequest.getRequest(), existingAssignments);

    }

    private void evaluateDeleteAssignments(AssignmentRequest existingAssignmentRequest) {

        //calling drools rules for validation
        validationModelService.validateRequest(existingAssignmentRequest);

        // we are mocking delete rejected status
        insertExistingRecordsAfterDroolValidation(existingAssignmentRequest);
    }

    public void checkAllApproved(AssignmentRequest parsedAssignmentRequest) {
        // decision block
        List<RoleAssignment> createApprovedAssignments = parsedAssignmentRequest.getRequestedRoles().stream()
            .filter(role -> role.getStatus().equals(Status.APPROVED)).toList();

        if (createApprovedAssignments.size() == parsedAssignmentRequest.getRequestedRoles().size()) {
            executeCreateRequest(parsedAssignmentRequest);
        } else {
            List<UUID> rejectedAssignmentIds = parsedAssignmentRequest.getRequestedRoles().stream()
                .filter(role -> role.getStatus().equals(Status.REJECTED)).map(RoleAssignment::getId).toList();
            rejectCreateRequest(parsedAssignmentRequest, rejectedAssignmentIds);
        }
    }

    private void rejectCreateRequest(AssignmentRequest parsedAssignmentRequest, List<UUID> rejectedAssignmentIds) {
        // Insert parsedAssignmentRequest.getRequestedRoles() records into history table with status REJECTED
        insertRequestedRole(parsedAssignmentRequest, Status.REJECTED, rejectedAssignmentIds);

        // Update request status to REJECTED
        parsedAssignmentRequest.getRequest().setStatus(Status.REJECTED);
        requestEntity.setStatus(Status.REJECTED.toString());
        if (CollectionUtils.isNotEmpty(rejectedAssignmentIds)) {
            requestEntity.setLog(REQUEST_REJECTION_MSG + rejectedAssignmentIds);
            parsedAssignmentRequest.getRequest().setLog(REQUEST_REJECTION_MSG + rejectedAssignmentIds);
        }


        persistenceService.updateRequest(requestEntity);
    }

    private void executeCreateRequest(AssignmentRequest parsedAssignmentRequest) {
        // Insert parsedAssignmentRequest.getRequestedRoles() records into live table
        moveHistoryRecordsToLiveTable(requestEntity);

        // Insert parsedAssignmentRequest.getRequestedRoles() records into history table with status LIVE
        insertRequestedRole(parsedAssignmentRequest, Status.LIVE, emptyUUIds);

        // Update request status to approved
        parsedAssignmentRequest.getRequest().setStatus(Status.APPROVED);
        parsedAssignmentRequest.getRequest().setLog(REQUEST_APPROVAL_MSG);
        requestEntity.setLog(REQUEST_APPROVAL_MSG);
        requestEntity.setStatus(Status.APPROVED.toString());
        persistenceService.updateRequest(requestEntity);
    }
}
