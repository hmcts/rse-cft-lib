package uk.gov.hmcts.reform.roleassignment.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@NoArgsConstructor
@Validated
@Slf4j
public class RoleAssignmentResource {

    @JsonProperty("roleAssignmentResponse")
    private List<? extends Assignment> roleAssignmentResponse;


    public RoleAssignmentResource(List<Assignment> roleAssignmentResponse, String actorId) {
        this.roleAssignmentResponse = roleAssignmentResponse;
    }

    public RoleAssignmentResource(List<? extends Assignment> roleAssignmentResponse) {
        this.roleAssignmentResponse = roleAssignmentResponse;
    }
}
