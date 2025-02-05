package uk.gov.hmcts.reform.roleassignment.domain.service.queryroles;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.MultipleQueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleConfig;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleConfigRole;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.util.ValidationUtil;

import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class QueryRoleAssignmentOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(QueryRoleAssignmentOrchestrator.class);
    private final PersistenceService persistenceService;


    public ResponseEntity<RoleAssignmentResource> retrieveRoleAssignmentsByQueryRequest(QueryRequest queryRequest,
                                                                                        Integer pageNumber,
                                                                                        Integer size, String sort,
                                                                                        String direction,
                                                                                        Boolean includeLabels) {

        ValidationUtil.validateQueryRequests(Collections.singletonList(queryRequest));

        List<Assignment> assignmentList =
            persistenceService.retrieveRoleAssignmentsByQueryRequest(
                queryRequest,
                pageNumber,
                size,
                sort,
                direction,
                false
            );
        return prepareQueryResponse(assignmentList, includeLabels);

    }



    public ResponseEntity<RoleAssignmentResource> retrieveRoleAssignmentsByMultipleQueryRequest(
        MultipleQueryRequest queryRequest,
        Integer pageNumber,
        Integer size, String sort, String direction, Boolean includeLabels) {


        ValidationUtil.validateQueryRequests(queryRequest.getQueryRequests());

        List<Assignment> assignmentList =
            persistenceService.retrieveRoleAssignmentsByMultipleQueryRequest(
                queryRequest,
                pageNumber,
                size,
                sort,
                direction,
                false
            );
        return prepareQueryResponse(assignmentList, includeLabels);

    }

    /**
         * prepare final query response based on search criteria.
         * @param assignmentList must not be {@literal null}.
         * @return ResponseEntity RoleAssignmentResource
     */
    private ResponseEntity<RoleAssignmentResource> prepareQueryResponse(List<Assignment> assignmentList,
                                                                        Boolean includeLabels) {
        var responseHeaders = new HttpHeaders();
        responseHeaders.add(
            "Total-Records",
            Long.toString(persistenceService.getTotalRecords())
        );

        if (Boolean.TRUE.equals(includeLabels) && !assignmentList.isEmpty()) {
            RoleConfig roleConfig = RoleConfig.getRoleConfig();

            assignmentList.forEach(assignment -> {
                RoleConfigRole roleConfigRole = roleConfig.get(
                    assignment.getRoleName(),
                    assignment.getRoleCategory(),
                    assignment.getRoleType()
                );

                if (roleConfigRole != null) {
                    assignment.setRoleLabel(roleConfigRole.getLabel());
                }
            });
        }

        return ResponseEntity.status(HttpStatus.OK).headers(responseHeaders).body(
            new RoleAssignmentResource(assignmentList));
    }
}
