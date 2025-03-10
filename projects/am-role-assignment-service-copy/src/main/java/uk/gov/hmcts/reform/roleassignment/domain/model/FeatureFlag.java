package uk.gov.hmcts.reform.roleassignment.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeatureFlag {
    String flagName;
    boolean status;
}
