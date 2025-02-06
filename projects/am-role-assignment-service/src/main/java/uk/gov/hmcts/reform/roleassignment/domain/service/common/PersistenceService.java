package uk.gov.hmcts.reform.roleassignment.domain.service.common;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.UnprocessableEntityException;
import uk.gov.hmcts.reform.roleassignment.data.DatabaseChangelogLockEntity;
import uk.gov.hmcts.reform.roleassignment.data.DatabseChangelogLockRepository;
import uk.gov.hmcts.reform.roleassignment.data.FlagConfig;
import uk.gov.hmcts.reform.roleassignment.data.FlagConfigRepository;
import uk.gov.hmcts.reform.roleassignment.data.HistoryEntity;
import uk.gov.hmcts.reform.roleassignment.data.HistoryRepository;
import uk.gov.hmcts.reform.roleassignment.data.RequestEntity;
import uk.gov.hmcts.reform.roleassignment.data.RequestRepository;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntity;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentRepository;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.MultipleQueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.launchdarkly.FeatureToggleService;
import uk.gov.hmcts.reform.roleassignment.util.PersistenceUtil;
import uk.gov.hmcts.reform.roleassignment.util.ValidationUtil;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specification.where;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByActorIds;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByAttributes;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByAuthorisations;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByClassification;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByGrantType;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByHasAttributes;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByReadOnly;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByRoleCategories;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByRoleName;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByRoleType;
import static uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntitySpecifications.searchByValidDate;

@Service
public class PersistenceService {

    //1. StoreRequest which will insert records in request and history table with log,
    //2. Insert new Assignment record with updated Status in historyTable
    //3. Update Request Status
    //4. Store live record in the roleAssignmentTable

    private HistoryRepository historyRepository;
    private RequestRepository requestRepository;
    private RoleAssignmentRepository roleAssignmentRepository;
    private PersistenceUtil persistenceUtil;
    private DatabseChangelogLockRepository databseChangelogLockRepository;
    private FlagConfigRepository flagConfigRepository;
    @Autowired
    private FeatureToggleService featureToggleService;

    @Value("${roleassignment.query.sortcolumn}")
    private String sortColumn;

    @Value("${roleassignment.query.size}")
    private Integer defaultSize;

    @Autowired
    EntityManager entityManager;

    public PersistenceService(HistoryRepository historyRepository, RequestRepository requestRepository,
                              RoleAssignmentRepository roleAssignmentRepository, PersistenceUtil persistenceUtil,
                              DatabseChangelogLockRepository databseChangelogLockRepository,
                              FlagConfigRepository flagConfigRepository) {
        this.historyRepository = historyRepository;
        this.requestRepository = requestRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.persistenceUtil = persistenceUtil;
        this.databseChangelogLockRepository = databseChangelogLockRepository;
        this.flagConfigRepository = flagConfigRepository;
    }

    @Transactional
    public RequestEntity persistRequest(Request request) {

        //Prepare request entity
        var requestEntity = persistenceUtil.convertRequestToEntity(request);

        //Persist the request entity
        return requestRepository.save(requestEntity);

    }

    @Transactional
    public void updateRequest(RequestEntity requestEntity) {
        //Persist the request entity
        requestRepository.save(requestEntity);
    }

    @Transactional
    public void persistHistoryEntities(Collection<HistoryEntity> historyEntityList) {
        historyEntityList.forEach(historyEntity -> entityManager.persist(historyEntity));
        entityManager.flush();
    }

    @Transactional
    public void persistRoleAssignments(Collection<RoleAssignment> roleAssignments) {
        //Persist the role assignment entity
        Set<RoleAssignmentEntity> roleAssignmentEntities = roleAssignments.stream().map(
            roleAssignment -> persistenceUtil.convertRoleAssignmentToEntity(roleAssignment, true)
        ).collect(Collectors.toSet());
        roleAssignmentEntities.forEach(roleAssignmentEntity -> entityManager.persist(roleAssignmentEntity));
        entityManager.flush();
    }

    public List<RoleAssignment> getAssignmentsByProcess(String process, String reference, String status) {

        Set<HistoryEntity> historyEntities = historyRepository.findByReference(process, reference, status);
        //convert into model class
        return historyEntities.stream()
            .map(persistenceUtil::convertHistoryEntityToRoleAssignment).toList();
    }

    @Transactional
    public void deleteRoleAssignment(RoleAssignment roleAssignment) {
        //Persist the role assignment entity
        RoleAssignmentEntity entity = persistenceUtil.convertRoleAssignmentToEntity(roleAssignment, false);
        roleAssignmentRepository.delete(entity);
    }

    @Transactional
    public void deleteRoleAssignmentByActorId(String actorId) {
        roleAssignmentRepository.deleteByActorId(actorId);
    }

    @Transactional
    public DatabaseChangelogLockEntity releaseDatabaseLock(int id) {
        databseChangelogLockRepository.releaseLock(id);
        return databseChangelogLockRepository.getById(id);
    }

    @Transactional
    public List<RoleAssignment> getAssignmentsByActor(String actorId) {
        try {
            Set<RoleAssignmentEntity> roleAssignmentEntities = roleAssignmentRepository.findByActorId(actorId);
            //convert into model class
            return roleAssignmentEntities.stream().map(persistenceUtil::convertEntityToRoleAssignment)
                .toList();
        } catch (Exception sqlException) {
            throw new UnprocessableEntityException("SQL Error get by actor id: "
                                                       + sqlException.getMessage());
        }
    }


    public List<Assignment> retrieveRoleAssignmentsByQueryRequest(QueryRequest searchRequest,
                                                                  Integer pageNumber,
                                                                  Integer size, String sort,
                                                                  String direction,
                                                                  boolean existingFlag) {

        List<String> roleTypes = addCaseTypeIfCaseIdExists(searchRequest);

        Page<RoleAssignmentEntity> pageRoleAssignmentEntities = roleAssignmentRepository.findAll(
            Objects.requireNonNull(Objects.requireNonNull(
                Objects.requireNonNull(
                    Objects.requireNonNull(
                        Objects.requireNonNull(
                            Objects.requireNonNull(
                                Objects.requireNonNull(
                                    Objects.requireNonNull(
                                        where(
                                            searchByActorIds(searchRequest.getActorId())))
                                        .and(searchByGrantType(searchRequest.getGrantType())))
                                    .and(searchByValidDate(searchRequest.getValidAt())))
                                .and(searchByAttributes(searchRequest.getAttributes())))
                            .and(searchByRoleType(roleTypes)))
                        .and(searchByRoleName(searchRequest.getRoleName())))
                    .and(searchByClassification(searchRequest.getClassification())))
                                       .and(searchByRoleCategories(searchRequest.getRoleCategory())))
                .and(searchByAuthorisations(searchRequest.getAuthorisations())),
            PageRequest.of(
                (pageNumber != null
                    && pageNumber > 0) ? pageNumber : 0,
                (size != null
                    && size > 0) ? size : defaultSize,
                Sort.by(
                    (direction != null) ? Sort.Direction.fromString(direction) : Sort.DEFAULT_DIRECTION,
                    (sort != null) ? sort : sortColumn
                )
            )
        );

        PageHolder.holder.set(pageRoleAssignmentEntities);

        return prepareQueryRequestResponse(existingFlag);
    }

    public List<Assignment> retrieveRoleAssignmentsByMultipleQueryRequest(MultipleQueryRequest multipleQueryRequest,
                                                                          Integer pageNumber,
                                                                          Integer size, String sort,
                                                                          String direction,
                                                                          boolean existingFlag) {

        Specification<RoleAssignmentEntity> finalQuery = null;
        if (CollectionUtils.isNotEmpty(multipleQueryRequest.getQueryRequests())) {

            List<String> roleTypes = addCaseTypeIfCaseIdExists(multipleQueryRequest.getQueryRequests().get(0));

            Specification<RoleAssignmentEntity> initialQuery = Specification.where(
                searchByActorIds(multipleQueryRequest.getQueryRequests().get(0).getActorId()))
                .and(searchByGrantType(multipleQueryRequest.getQueryRequests().get(0).getGrantType()))
                .and(searchByValidDate(multipleQueryRequest.getQueryRequests().get(0).getValidAt()))
                .and(searchByAttributes(multipleQueryRequest.getQueryRequests().get(0).getAttributes()))
                .and(searchByRoleType(roleTypes))
                .and(searchByRoleName(multipleQueryRequest.getQueryRequests().get(0).getRoleName()))
                .and(searchByClassification(multipleQueryRequest.getQueryRequests().get(0).getClassification()))
                .and(searchByRoleCategories(multipleQueryRequest.getQueryRequests().get(0).getRoleCategory()))
                .and(searchByAuthorisations(multipleQueryRequest.getQueryRequests().get(0).getAuthorisations()))
                .and(searchByHasAttributes(multipleQueryRequest.getQueryRequests().get(0).getHasAttributes()))
                .and(searchByReadOnly(multipleQueryRequest.getQueryRequests().get(0).getReadOnly()));


            if (multipleQueryRequest.getQueryRequests().size() > 1) {
                for (var i = 1; i < multipleQueryRequest.getQueryRequests().size(); i++) {

                    List<String> roleTypesMulti = addCaseTypeIfCaseIdExists(multipleQueryRequest
                                                                                .getQueryRequests()
                                                                                .get(i));

                    finalQuery = initialQuery.or(
                          searchByActorIds(multipleQueryRequest.getQueryRequests().get(i).getActorId())
                           .and(searchByRoleName(multipleQueryRequest.getQueryRequests().get(i).getRoleName()))
                           .and(searchByHasAttributes(multipleQueryRequest.getQueryRequests().get(i)
                                                          .getHasAttributes()))
                           .and(searchByAuthorisations(multipleQueryRequest.getQueryRequests()
                                                           .get(i).getAuthorisations()))
                           .and(searchByGrantType(multipleQueryRequest.getQueryRequests().get(i).getGrantType()))
                           .and(searchByValidDate(multipleQueryRequest.getQueryRequests().get(i).getValidAt()))
                           .and(searchByAttributes(multipleQueryRequest.getQueryRequests().get(i).getAttributes()))
                           .and(searchByRoleType(roleTypesMulti))
                           .and(searchByClassification(multipleQueryRequest.getQueryRequests().get(i)
                                                           .getClassification()))
                           .and(searchByRoleCategories(multipleQueryRequest.getQueryRequests()
                                                           .get(i).getRoleCategory()))
                           .and(searchByReadOnly(multipleQueryRequest.getQueryRequests().get(i).getReadOnly())));
                    initialQuery = finalQuery;
                }
            } else {
                finalQuery = initialQuery;
            }

        }


        Page<RoleAssignmentEntity> pageRoleAssignmentEntities  = roleAssignmentRepository.findAll(
            finalQuery,
            PageRequest.of(
                (pageNumber != null
                    && pageNumber > 0) ? pageNumber : 0,
                (size != null
                    && size > 0) ? size : defaultSize,
                Sort.by(
                    (direction != null) ? Sort.Direction.fromString(direction) : Sort.DEFAULT_DIRECTION,
                    (sort != null) ? sort : sortColumn
                )
            )
        );
        PageHolder.holder.set(pageRoleAssignmentEntities);

        return prepareQueryRequestResponse(existingFlag);
    }

    private List<Assignment> prepareQueryRequestResponse(boolean existingFlag) {
        List<Assignment> roleAssignmentList;
        if (!existingFlag) {
            roleAssignmentList = PageHolder.holder.get().stream()
                .map(persistenceUtil::convertEntityToRoleAssignment)
                .map(Assignment.class::cast)
                .toList();
        } else {
            roleAssignmentList = PageHolder.holder.get().stream()
                .map(persistenceUtil::convertEntityToExistingRoleAssignment)
                .map(Assignment.class::cast)
                .toList();
        }

        return roleAssignmentList;
    }

    public List<RoleAssignment> getAssignmentById(UUID assignmentId) {
        Optional<RoleAssignmentEntity> roleAssignmentEntityOptional = roleAssignmentRepository.findById(assignmentId);
        if (roleAssignmentEntityOptional.isPresent()) {
            return roleAssignmentEntityOptional.stream()
                .map(persistenceUtil::convertEntityToRoleAssignment)
                .toList();
        }
        return Collections.emptyList();
    }

    public long getTotalRecords() {
        return PageHolder.holder.get() != null ? PageHolder.holder.get()
            .getTotalElements() : 0L;

    }

    public boolean getStatusByParam(String flagName, String envName) {
        if (StringUtils.isEmpty(envName)) {
            envName = System.getenv("LAUNCH_DARKLY_ENV");
        }
        return flagConfigRepository.findByFlagNameAndEnv(flagName, envName).getStatus();
    }

    public FlagConfig persistFlagConfig(FlagConfig flagConfig) {
        return flagConfigRepository.save(flagConfig);
    }

    public List<String> addCaseTypeIfCaseIdExists(QueryRequest queryRequest) {
        List<String> roleTypes = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(queryRequest.getRoleType())) {
            roleTypes.addAll(queryRequest.getRoleType());
        }
        if (ValidationUtil.doesKeyAttributeExist(queryRequest.getAttributes(), "caseId")
            && !roleTypes.contains("CASE")) {
            roleTypes.add(RoleType.CASE.name());
        }
        return roleTypes;
    }

}
