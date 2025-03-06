package uk.gov.hmcts.reform.roleassignment.domain.service.common;


import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.BadRequestException;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.PredicateValidator;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RequestType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.util.Constants;
import uk.gov.hmcts.reform.roleassignment.util.CorrelationInterceptorUtil;
import uk.gov.hmcts.reform.roleassignment.util.CreatedTimeComparator;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;
import uk.gov.hmcts.reform.roleassignment.util.ValidationUtil;
import uk.gov.hmcts.reform.roleassignment.versions.V1;

import java.text.ParseException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ParseRequestService {

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private CorrelationInterceptorUtil correlationInterceptorUtil;

    @Value("#{new Boolean('${org.request.byPassOrgDroolRule}')}")
    private boolean byPassOrgDroolRule;

    public AssignmentRequest parseRequest(AssignmentRequest assignmentRequest, RequestType requestType)
        throws ParseException {

        var request = assignmentRequest.getRequest();
        request.setByPassOrgDroolRule(byPassOrgDroolRule);
        ValidationUtil.validateAssignmentRequest(assignmentRequest);

        //2. Request Parsing
        //a. Extract client Id and place in the request
        request.setClientId(securityUtils.getServiceName());
        //b. Extract AuthenticatedUser Id from the User token and place in the request.
        request.setAuthenticatedUserId(securityUtils.getUserId());
        //c. Set Status=Created and created Time = now
        request.setStatus(Status.CREATED);
        request.setRequestType(requestType);
        request.setCreated(ZonedDateTime.now(ZoneOffset.UTC));
        //d. correlationId if it is empty then generate a new value and set.
        setCorrelationId(request);
        //3. RoleAssignment Parsing
        //a. Copy process and reference from the request to RoleAssignment
        //b. Set Status=Created and statusSequenceNumber from Status Enum
        //c. created Time = now
        List<RoleAssignment> requestedAssignments = (List<RoleAssignment>) assignmentRequest.getRequestedRoles();

        requestedAssignments.forEach(requestedAssignment -> {
            requestedAssignment.setProcess(request.getProcess());
            requestedAssignment.setReference(request.getReference());
            requestedAssignment.setStatus(Status.CREATE_REQUESTED);
            requestedAssignment.setCreated(ZonedDateTime.now(ZoneOffset.UTC));
        });
        requestedAssignments.sort(new CreatedTimeComparator());
        var parsedRequest = new AssignmentRequest(new Request(), Collections.emptyList());
        parsedRequest.setRequest(request);
        parsedRequest.setRequestedRoles(requestedAssignments);

        return parsedRequest;
    }

    private void setCorrelationId(Request request) {
        var httpServletRequest = ((ServletRequestAttributes) RequestContextHolder
            .currentRequestAttributes())
            .getRequest();
        request.setCorrelationId(correlationInterceptorUtil.preHandle(httpServletRequest));
    }

    public String getRequestCorrelationId() {
        var httpServletRequest = ((ServletRequestAttributes) RequestContextHolder
            .currentRequestAttributes())
            .getRequest();
        return correlationInterceptorUtil.preHandle(httpServletRequest);
    }

    public void removeCorrelationLog() {
        correlationInterceptorUtil.afterCompletion();
    }

    public Request prepareDeleteRequest(String process, String reference, String actorId, String assignmentId) {
        if (!StringUtils.isEmpty(actorId)) {
            ValidationUtil.validateId(Constants.NUMBER_TEXT_HYPHEN_PATTERN, actorId);
        }

        var request = Request.builder()
            .clientId(securityUtils.getServiceName())
            .authenticatedUserId(securityUtils.getUserId())
            .status(Status.CREATED)
            .requestType(RequestType.DELETE)
            .created(ZonedDateTime.now(ZoneOffset.UTC))
            .process(process)
            .byPassOrgDroolRule(byPassOrgDroolRule)
            .reference(reference)
            .build();
        setCorrelationId(request);
        setAssignerId(request);

        if (!StringUtils.isEmpty(assignmentId)) {
            ValidationUtil.validateId(Constants.UUID_PATTERN, assignmentId);
            request.setRoleAssignmentId(UUID.fromString(assignmentId));
        }
        return request;
    }

    private void setAssignerId(Request request) {
        var httpServletRequest = ((ServletRequestAttributes) RequestContextHolder
            .currentRequestAttributes())
            .getRequest();
        String assignerId = httpServletRequest.getHeader("assignerId");

        if (StringUtils.isBlank(assignerId)) {
            request.setAssignerId(request.getAuthenticatedUserId());
        } else {
            ValidationUtil.validateId(Constants.NUMBER_TEXT_HYPHEN_PATTERN, assignerId);
            request.setAssignerId(assignerId);
        }
    }

    public void validateGetAssignmentsByActorIdAndCaseId(String actorId, String caseId, String roleType) {
        if (StringUtils.isEmpty(roleType)
            || !PredicateValidator.stringCheckPredicate(RoleType.CASE.name()).test(roleType)) {
            throw new BadRequestException(V1.Error.INVALID_ROLE_TYPE);
        }

        if (StringUtils.isEmpty(actorId) && StringUtils.isEmpty(caseId)) {
            throw new BadRequestException(V1.Error.INVALID_ACTOR_AND_CASE_ID);
        }

        if (StringUtils.isNotEmpty(actorId)) {
            ValidationUtil.validateId(Constants.NUMBER_TEXT_HYPHEN_PATTERN, actorId);
        }
        if (StringUtils.isNotEmpty(caseId)) {
            ValidationUtil.validateCaseId(caseId);
        }
    }
}
