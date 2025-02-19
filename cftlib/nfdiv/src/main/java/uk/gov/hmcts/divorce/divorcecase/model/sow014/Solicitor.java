package uk.gov.hmcts.divorce.divorcecase.model.sow014;

import lombok.Data;
import uk.gov.hmcts.ccd.sdk.api.CCD;

@Data
public class Solicitor {

    private String solicitorId;
    private Long organisationId;
    private String reference;
    private String role;
    @CCD(
        showCondition = "version=\"NEVER_SHOW\""
    )
    private String version;
    @CCD(
        label = "First name"
    )
    private String forename;

    @CCD(
        label = "Last name"
    )
    private String surname;
}
