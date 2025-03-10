package uk.gov.hmcts.reform.roleassignment.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;


@Data
@NoArgsConstructor
@Validated
@Slf4j
public class RoleAssignmentDeleteResource {


    @JsonProperty("roleRequest")
    private Request roleRequest;

    public RoleAssignmentDeleteResource(Request roleRequest) {
        this.roleRequest = roleRequest;
    }


}
