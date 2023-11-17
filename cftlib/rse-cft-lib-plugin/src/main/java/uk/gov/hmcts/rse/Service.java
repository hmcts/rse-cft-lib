package uk.gov.hmcts.rse;

import com.google.common.base.CaseFormat;

public enum Service {
    amRoleAssignmentService,
    ccdDataStoreApi,
    // 'application' is definition store entry module
    ccdDefinitionStoreApi("application"),
    ccdUserProfileApi("user-profile-api"),
    aacManageCaseAssignment,
    ccdCaseDocumentAmApi,
    dgDocassemblyApi;

    private final String id;

    Service() {
        this.id = null;
    }

    Service(String id) {
        this.id = id;
    }

    public String id() {
        if (id != null) {
            return id;
        }
        return CaseFormat.LOWER_CAMEL
                .to(CaseFormat.LOWER_HYPHEN, toString())
                .replace("_", "-");
    }
}
