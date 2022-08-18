package uk.gov.hmcts.libconsumer;

import java.nio.charset.StandardCharsets;

import com.google.common.io.Resources;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLibConfigurer;

@Component
public class CFTLibConfig implements CFTLibConfigurer {
    @SneakyThrows
    @Override
    public void configure(CFTLib lib) {
        lib.createIdamUser("a@b.com", "caseworker", "caseworker-divorce", "caseworker-divorce-solicitor",
            "caseworker-caa", "pui-organisation-manager");
        lib.createProfile("banderous", "DIVORCE", "NO_FAULT_DIVORCE", "Submitted");
        lib.createRoles(
            "caseworker-divorce-courtadmin_beta",
            "caseworker-divorce-superuser",
            "caseworker-divorce-courtadmin-la",
            "caseworker-divorce-courtadmin",
            "caseworker-divorce-solicitor",
            "caseworker-divorce-pcqextractor",
            "caseworker-divorce-systemupdate",
            "caseworker-divorce-bulkscan",
            "caseworker-caa",
            "citizen"
        );
        var json = Resources.toString(Resources.getResource("cftlib-am-role-assignments.json"), StandardCharsets.UTF_8);
        // Import role assignments replacing any existing for the user
        lib.configureRoleAssignments(json, true);


        lib.importDefinition(Resources.toByteArray(Resources.getResource("NFD-dev.xlsx")));
    }
}
