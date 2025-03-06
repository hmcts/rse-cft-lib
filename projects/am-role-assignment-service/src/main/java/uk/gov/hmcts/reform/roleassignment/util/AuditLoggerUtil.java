package uk.gov.hmcts.reform.roleassignment.util;

import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.Assignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignment;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentRequestResource;
import uk.gov.hmcts.reform.roleassignment.domain.model.RoleAssignmentResource;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
@Singleton
public class AuditLoggerUtil {

    private AuditLoggerUtil() {

    }

    public static List<UUID> buildAssignmentIds(final ResponseEntity<RoleAssignmentRequestResource> response) {
        if (response.getBody() instanceof RoleAssignmentRequestResource) {
            var roleAssignmentRequestResource = response.getBody();
            if (roleAssignmentRequestResource != null) {
                return roleAssignmentRequestResource.getRoleAssignmentRequest().getRequestedRoles().stream().limit(10)
                    .map(RoleAssignment::getId)
                    .toList();
            }
        }
        return List.of();
    }

    public static Set<String> buildActorIds(final ResponseEntity<RoleAssignmentRequestResource> response) {
        if (response.getBody() instanceof RoleAssignmentRequestResource) {
            var roleAssignmentRequestResource = response.getBody();
            if (roleAssignmentRequestResource != null) {
                return roleAssignmentRequestResource.getRoleAssignmentRequest().getRequestedRoles().stream().limit(10)
                    .map(RoleAssignment::getActorId)
                    .collect(Collectors.toSet());
            }
        }
        return Set.of();
    }

    public static List<String> buildRoleNames(final ResponseEntity<RoleAssignmentRequestResource> response) {
        if (response.getBody() instanceof RoleAssignmentRequestResource) {
            var roleAssignmentRequestResource = response.getBody();
            if (roleAssignmentRequestResource != null) {
                return roleAssignmentRequestResource.getRoleAssignmentRequest().getRequestedRoles().stream().limit(10)
                    .map(RoleAssignment::getRoleName)
                    .toList();
            }
        }
        return List.of();
    }

    public static Set<String> buildCaseIds(final ResponseEntity<RoleAssignmentRequestResource> response) {
        Set<String> caseIds = new HashSet<>();
        if (response.getBody() instanceof RoleAssignmentRequestResource) {
            var roleAssignmentRequestResource = response.getBody();
            if (roleAssignmentRequestResource != null) {
                roleAssignmentRequestResource.getRoleAssignmentRequest().getRequestedRoles()
                    .stream().map(RoleAssignment::getAttributes).forEach(obj -> obj.forEach((key, value) -> {
                        if (key.equals("caseId")) {
                            caseIds.add(value.asText());
                        }
                    }));
            }

        }
        return caseIds;
    }

    public static List<UUID> getAssignmentIds(final ResponseEntity<RoleAssignmentResource> response) {
        var roleAssignmentResource = response.getBody();
        if (roleAssignmentResource != null) {
            return roleAssignmentResource.getRoleAssignmentResponse().stream().limit(10)
                .map(Assignment::getId)
                .toList();
        }
        return List.of();
    }

    public static Set<String> getActorIds(final ResponseEntity<RoleAssignmentResource> response) {
        var roleAssignmentResource = response.getBody();
        if (roleAssignmentResource != null) {
            return roleAssignmentResource.getRoleAssignmentResponse().stream().limit(10)
                .map(Assignment::getActorId)
                .collect(Collectors.toSet());
        }
        return Set.of();
    }

    public static List<UUID> searchAssignmentIds(final ResponseEntity<RoleAssignmentResource> response) {
        var roleAssignmentResource = response.getBody();
        if (roleAssignmentResource != null) {
            List<? extends Assignment> roleAssignmentResponse =  roleAssignmentResource.getRoleAssignmentResponse();
            return roleAssignmentResponse.stream().limit(10)
                .map(Assignment::getId)
                .toList();
        }
        return List.of();
    }

    public static String sizeOfAssignments(final ResponseEntity<RoleAssignmentResource> response) {
        var roleAssignmentResource = response.getBody();
        if (roleAssignmentResource != null) {
            return String.valueOf(roleAssignmentResource.getRoleAssignmentResponse().size());
        }
        return null;
    }
}
