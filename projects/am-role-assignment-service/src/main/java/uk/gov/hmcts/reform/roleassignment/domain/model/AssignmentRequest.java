package uk.gov.hmcts.reform.roleassignment.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Collection;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class AssignmentRequest {
    @JsonProperty(value = "roleRequest")
    private Request request;

    @JsonProperty(value = "requestedRoles")
    private Collection<RoleAssignment> requestedRoles;
}
