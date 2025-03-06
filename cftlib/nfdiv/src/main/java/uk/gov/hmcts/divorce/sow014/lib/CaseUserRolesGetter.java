package uk.gov.hmcts.divorce.sow014.lib;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.sdk.CaseAssignedUserRole;
import uk.gov.hmcts.divorce.client.RoleAssignmentService;
import uk.gov.hmcts.divorce.client.request.MultipleQueryRequest;
import uk.gov.hmcts.divorce.client.request.QueryRequest;
import uk.gov.hmcts.divorce.client.request.RoleType;
import uk.gov.hmcts.divorce.client.response.RoleAssignmentResource;
import uk.gov.hmcts.divorce.idam.IdamService;
import uk.gov.hmcts.divorce.idam.User;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Slf4j
@Service
public class CaseUserRolesGetter {

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private IdamService idamService;

    @Autowired
    private ObjectMapper getMapper;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    public String getUserId() {
        var auth = httpServletRequest.getHeader(AUTHORIZATION);
        User solicitorUser = idamService.retrieveUser(auth);
        String userId = solicitorUser.getUserDetails().getUid();
        return userId;
    }

    @SneakyThrows
    public boolean isAdminCaseworker() {
        var auth = httpServletRequest.getHeader(AUTHORIZATION);
        User user = idamService.retrieveUser(auth);
        List<String> roles = user.getUserDetails().getRoles();
        return roles.contains("caseworker-divorce-courtadmin_beta") || roles.contains("caseworker-divorce-systemupdate");
    }

    @SneakyThrows
    public Set<String> getUserRoles() {
        List<CaseAssignedUserRole> roleAssignments = decodeHeader(httpServletRequest.getHeader("roleAssignments"));
        Set<String> roles = roleAssignments.stream().map(CaseAssignedUserRole::getCaseRole).collect(Collectors.toSet());
        log.info("RoleAssignments: {}", roles);
        return roles;
    }

    public Set<String> getUserRoles(String caseId, String userId) {
        RoleAssignmentResource roleAssignmentResource = roleAssignmentService.performSearch(buildQuery(caseId, userId));

        var caseIdError = new RuntimeException(RoleAssignmentAttributes.ATTRIBUTE_NOT_DEFINED);
        var assignedUserRoles = roleAssignmentResource.getRoleAssignmentResponse().stream()
            .map(roleAssignment ->
                new CaseAssignedUserRole(
                    roleAssignment.getAttributes().getCaseId().orElseThrow(() -> caseIdError),
                    roleAssignment.getActorId(),
                    roleAssignment.getRoleName()
                )
            )
            .collect(Collectors.toList());

        Set<String> roles = assignedUserRoles.stream().map(CaseAssignedUserRole::getCaseRole).collect(Collectors.toSet());
        log.info("RoleAssignments: {}", roles);
        return roles;
    }

    @SneakyThrows
    private List<CaseAssignedUserRole> decodeHeader(String roles) throws JsonProcessingException {
        if (StringUtils.isBlank(roles)) {
            return List.of();
        }
        log.info("roles: {}", roles);

        String roleAssignments = new String(Base64.getDecoder().decode(roles));
        log.info("roleAssignments: {}", roleAssignments);

        return getMapper.readValue(roleAssignments, new TypeReference<List<CaseAssignedUserRole>>() {
        });
    }

    private MultipleQueryRequest buildQuery(String caseId, String userId) {
        QueryRequest queryRequest = QueryRequest.builder()
            .actorId(List.of(userId))
            .roleType(List.of(RoleType.CASE))
            .validAt(LocalDateTime.now())
            .attributes(Map.of("caseId", List.of(caseId)))
            .build();

        return MultipleQueryRequest.builder().queryRequests(singletonList(queryRequest)).build();
    }
}
