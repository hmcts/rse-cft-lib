package uk.gov.hmcts.reform.roleassignment.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
@Getter
public class FlagRequest {

    private String flagName;
    private String env;
    private String serviceName;
    private Boolean status;
}
