package uk.gov.hmcts.reform.roleassignment.befta;

import uk.gov.hmcts.befta.BeftaMain;
import uk.gov.hmcts.befta.TestAutomationConfig;

public class RoleAssignmentBeftaMain {

    private RoleAssignmentBeftaMain() {
    }

    public static void main(String[] args) {

        BeftaMain.main(args, TestAutomationConfig.INSTANCE, new RoleAssignmentTestAutomationAdapter(),
                       RasDefaultMultiSourceFeatureToggleService.INSTANCE);
    }
}
