package uk.gov.hmcts.reform.roleassignment.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@Validated
public class RoleAssignmentRequestResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleAssignmentRequestResource.class);

    @JsonProperty("roleAssignmentResponse")
    private AssignmentRequest roleAssignmentRequest;

    public RoleAssignmentRequestResource(@NonNull AssignmentRequest roleAssignmentRequest) {
        this.roleAssignmentRequest = roleAssignmentRequest;
    }
}

