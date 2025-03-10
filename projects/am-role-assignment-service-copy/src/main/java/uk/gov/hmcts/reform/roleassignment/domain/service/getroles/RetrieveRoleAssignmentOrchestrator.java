package uk.gov.hmcts.reform.roleassignment.domain.service.getroles;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.UnprocessableEntityException;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleConfigRole;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PrepareResponseService;
import uk.gov.hmcts.reform.roleassignment.util.Constants;
import uk.gov.hmcts.reform.roleassignment.util.JacksonUtils;
import uk.gov.hmcts.reform.roleassignment.util.ValidationUtil;

import java.util.List;

@Service
@RequestScope
public class RetrieveRoleAssignmentOrchestrator {

    private PersistenceService persistenceService;
    private PrepareResponseService prepareResponseService;

    public RetrieveRoleAssignmentOrchestrator(@Autowired PersistenceService persistenceService,
                                              @Autowired PrepareResponseService prepareResponseService) {
        this.persistenceService = persistenceService;
        this.prepareResponseService = prepareResponseService;
    }

    //1. call parse request service
    //2. Call retrieve Data service to fetch all required objects
    //3. Call Validation model service to create aggregation objects and apply drools validation rule
    //4. Call persistence to fetch requested assignment records
    //5. Call prepare response to make HATEOUS based response.

    public ResponseEntity<RoleAssignmentResource> getAssignmentsByActor(String actorId) {
        ValidationUtil.validateId(Constants.NUMBER_TEXT_HYPHEN_PATTERN, actorId);
        try {
            List<? extends Assignment> assignments = persistenceService.getAssignmentsByActor(actorId);
            return prepareResponseService.prepareRetrieveRoleResponse(
                assignments,
                actorId
            );
        } catch (Exception sqlException) {
            throw new UnprocessableEntityException("SQL Error get assignments by actor id: "
                                                       + sqlException.getMessage());
        }
    }

    public List<RoleConfigRole> getListOfRoles() {
        return JacksonUtils.getConfiguredRoles();
    }

}
