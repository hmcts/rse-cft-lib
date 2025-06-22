package uk.gov.hmcts.rse;

import com.google.common.base.CaseFormat;

import java.util.List;

public enum Service {
    amRoleAssignmentService,
    ccdDataStoreApi("--idam.client.secret=${IDAM_OAUTH2_DATA_STORE_CLIENT_SECRET:}"),
    ccdDefinitionStoreApi,
    ccdUserProfileApi,
    aacManageCaseAssignment("--idam.client.secret=${IDAM_OAUTH2_AAC_CLIENT_SECRET:}"),
    ccdCaseDocumentAmApi,
    dgDocassemblyApi,
    waTaskManagementApi("--idam.s2s-auth.microservice=wa_task_management");

    public final List<String> args;

    Service() {
        this.args = List.of();
    }

    Service(String... args) {
        this.args = List.of(args);
    }

    public String id() {
        return CaseFormat.LOWER_CAMEL
                .to(CaseFormat.LOWER_HYPHEN, toString())
                .replace("_", "-");
    }
}
