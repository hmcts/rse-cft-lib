package uk.gov.hmcts.divorce.divorcecase.model.sow014;

import lombok.Data;

@Data
public class Solicitor {

    private Long solicitorId;
    private Long organisationId;
    private String reference;
    private String role;
}
