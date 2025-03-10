package uk.gov.hmcts.reform.roleassignment.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.BadRequestException;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.QueryRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.Request;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.versions.V1;

import static uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType.CASE;
import static uk.gov.hmcts.reform.roleassignment.util.Constants.NUMBER_PATTERN;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
@Slf4j
public class ValidationUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ValidationUtil.class);

    private ValidationUtil() {
    }

    public static void validateDateTime(String strDate) throws ParseException {
        LOG.debug("validateDateTime");
        if (strDate.length() < 16) {
            throw new BadRequestException(String.format(
                "Incorrect date format %s",
                strDate
            ));
        }
        var simpleDateFormat = new SimpleDateFormat(Constants.DATE_PATTERN);
        simpleDateFormat.setLenient(false);
        Date javaDate;
        try {
            javaDate = simpleDateFormat.parse(strDate);
            if (LOG.isDebugEnabled() && javaDate != null) {
                LOG.debug(javaDate.toString());
            }
        } catch (ParseException e) {
            throw new BadRequestException(String.format(
                "Incorrect date format %s",
                strDate
            ));
        }
        assert javaDate != null;
        //we need to check valida date that it should not be like 32 dec 2020

    }

    public static void compareDateOrder(String beginTime, String endTime) throws ParseException {
        var sdf = new SimpleDateFormat(Constants.DATE_PATTERN);
        Date beginTimeP = sdf.parse(beginTime);
        Date endTimeP = sdf.parse(endTime);

        if (endTimeP.before(beginTimeP)) {
            throw new BadRequestException(
                String.format("The end time: %s takes place before the begin time: %s", endTime, beginTime));
        }
    }

    public static void validateId(String pattern, String inputString) {
        if (StringUtils.isEmpty(inputString)) {
            throw new BadRequestException("An input parameter is Null/Empty");
        } else if (!Pattern.matches(pattern, inputString)) {
            throw new BadRequestException(
                String.format("The input parameter: \"%s\", does not comply with the required pattern", inputString));
        }
    }

    public static boolean sanitiseCorrelationId(String inputString) {
        if (inputString != null && !inputString.isEmpty() && !Pattern.matches(Constants.UUID_PATTERN, inputString)) {
            throw new BadRequestException(
                String.format(
                    "The input parameter: \"%s\", does not comply with the required pattern",
                    inputString
                ));
        }
        return true;
    }

    public static void compareRoleType(String roleType) {
        var valid = false;
        for (RoleType realRole : RoleType.values()) {
            if (realRole.name().equalsIgnoreCase(roleType)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new BadRequestException(
                String.format("The Role Type parameter supplied: %s is not valid", roleType));
        }
    }

    public static void isRequestedRolesEmpty(Collection<?>... inputList) {
        for (Collection<?> collection : inputList) {
            if (CollectionUtils.isEmpty(collection)) {
                throw new BadRequestException("The Collection is empty");
            }
        }
    }

    public static void validateAssignmentRequest(AssignmentRequest assignmentRequest) throws ParseException {
        validateRoleRequest(assignmentRequest.getRequest());
        if (!(assignmentRequest.getRequest().isReplaceExisting())
            || (assignmentRequest.getRequest().isReplaceExisting()
            && !assignmentRequest.getRequestedRoles().isEmpty())) {
            isRequestedRolesEmpty(assignmentRequest.getRequestedRoles());
            validateRequestedRoles(assignmentRequest.getRequestedRoles());
        }
    }

    public static void validateRoleRequest(Request roleRequest) {
        if (roleRequest.isReplaceExisting()
            && ((StringUtils.isEmpty(roleRequest.getProcess())
            && (StringUtils.isEmpty(roleRequest.getReference())))
            || (!StringUtils.isEmpty(roleRequest.getProcess())
            && StringUtils.isEmpty(roleRequest.getReference()))
            || (StringUtils.isEmpty(roleRequest.getProcess())
            && !StringUtils.isEmpty(roleRequest.getReference()))
            )) {
            throw new BadRequestException(V1.Error.BAD_REQUEST_MISSING_PARAMETERS);
        }
        validateId(Constants.NUMBER_TEXT_HYPHEN_PATTERN, roleRequest.getAssignerId());
    }

    public static void validateRequestedRoles(Collection<RoleAssignment> requestedRoles) throws ParseException {

        for (RoleAssignment requestedRole : requestedRoles) {
            validateId(Constants.NUMBER_TEXT_HYPHEN_PATTERN, requestedRole.getActorId());
            if (requestedRole.getRoleType().equals(CASE)) {
                validateId(Constants.NUMBER_PATTERN, requestedRole.getAttributes().get("caseId").textValue());
            }
            validateBeginAndEndDates(requestedRole);
        }
    }

    private static void validateBeginAndEndDates(RoleAssignment requestedRole) throws ParseException {
        if (requestedRole.getBeginTime() != null) {
            if (requestedRole.getBeginTime().getYear() == 1970) {
                throw new BadRequestException(V1.Error.BAD_REQUEST_INVALID_DATETIME + " for beginTime");
            }
            validateDateTime(requestedRole.getBeginTime().toString());
        }
        if (requestedRole.getEndTime() != null) {
            if (requestedRole.getEndTime().getYear() == 1970) {
                throw new BadRequestException(V1.Error.BAD_REQUEST_INVALID_DATETIME + " for endTime");
            }
            validateDateTime(requestedRole.getEndTime().toString());
        }
        if (requestedRole.getBeginTime() != null && requestedRole.getEndTime() != null) {
            compareDateOrder(
                requestedRole.getBeginTime().toString(),
                requestedRole.getEndTime().toString()
            );
        }
    }

    public static void validateCaseId(String caseId) {
        validateId(NUMBER_PATTERN, caseId);
        if (caseId.length() != 16) {
            throw new BadRequestException(V1.Error.INVALID_CASE_ID);
        }
    }

    public static boolean csvContains(String value, String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).anyMatch(value::equals);
    }

    public static boolean doesKeyAttributeExist(Map<String, List<String>> attributes, String attribute) {
        if (MapUtils.isNotEmpty(attributes)) {
            return attributes.containsKey(attribute);
        } else {
            return false;
        }
    }

    public static void validateQueryRequests(List<QueryRequest> queryRequests) {
        if (queryRequests.isEmpty()) {
            throw new BadRequestException(V1.Error.BAD_QUERY_REQUEST_MISSING_CASEID_ACTORID);
        } else {
            queryRequests.forEach(ValidationUtil::validateQueryRequests);
        }
    }

    public static void validateQueryRequests(QueryRequest query) {
        if (isQueryRequestEmpty(query)) {
            throw new BadRequestException(V1.Error.BAD_QUERY_REQUEST_MISSING_CASEID_ACTORID);
        }
    }

    public static boolean isQueryRequestEmpty(QueryRequest query) {
        return CollectionUtils.isEmpty(query.getAuthorisations())
            && CollectionUtils.isEmpty(query.getActorId())
            && CollectionUtils.isEmpty(query.getClassification())
            && CollectionUtils.isEmpty(query.getGrantType())
            && CollectionUtils.isEmpty(query.getHasAttributes())
            && CollectionUtils.isEmpty(query.getRoleCategory())
            && CollectionUtils.isEmpty(query.getRoleName())
            && CollectionUtils.isEmpty(query.getRoleType())
            && query.getReadOnly() == null
            && query.getValidAt() == null
            && MapUtils.isEmpty(query.getAttributes());
    }
}
