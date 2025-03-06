package uk.gov.hmcts.divorce.client.request;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Builder
@EqualsAndHashCode
@ToString
@Data
public class RoleRequest {

    boolean replaceExisting;
    private String assignerId;
    private String process;
    private String reference;
}
