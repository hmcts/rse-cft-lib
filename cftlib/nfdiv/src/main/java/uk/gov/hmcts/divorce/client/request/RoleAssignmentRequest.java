package uk.gov.hmcts.divorce.client.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import uk.gov.hmcts.divorce.sow014.lib.RoleAssignment;

@EqualsAndHashCode
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleAssignmentRequest {

    private RoleRequest roleRequest;
    private List<RoleAssignment> requestedRoles;


    private RoleAssignmentRequest() {
        //Hidden constructor
    }

    public RoleAssignmentRequest(RoleRequest roleRequest, List<RoleAssignment> requestedRoles) {
        this.roleRequest = roleRequest;
        this.requestedRoles = requestedRoles;
    }

    public RoleRequest getRoleRequest() {
        return roleRequest;
    }

    public List<RoleAssignment> getRequestedRoles() {
        return requestedRoles;
    }

}
