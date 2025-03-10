package uk.gov.hmcts.reform.roleassignment.domain.service.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.PredicateValidator;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentRequestResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;

import java.util.List;

@Service
public class PrepareResponseService {

    public ResponseEntity<RoleAssignmentRequestResource> prepareCreateRoleResponse(
        AssignmentRequest roleAssignmentRequest) {

        // set clientId null to avoid it to expose in the response
        roleAssignmentRequest.getRequest().setClientId(null);


        if (PredicateValidator.assignmentRequestPredicate(roleAssignmentRequest.getRequest().getStatus())
            .test(Status.REJECTED)) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                new RoleAssignmentRequestResource(
                    roleAssignmentRequest));
        } else {
            return ResponseEntity.status(HttpStatus.CREATED).body(new RoleAssignmentRequestResource(
                roleAssignmentRequest));
        }

    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<RoleAssignmentResource> prepareRetrieveRoleResponse(
        List<? extends Assignment> roleAssignmentResponse, String actorId)  {
        return ResponseEntity.status(HttpStatus.OK).body(
            new RoleAssignmentResource((List<Assignment>) roleAssignmentResponse, actorId));
    }


}
