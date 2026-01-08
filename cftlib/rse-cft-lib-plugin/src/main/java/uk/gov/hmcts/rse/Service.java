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
    waTaskManagementApi(
        "--POSTGRES_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--POSTGRES_PORT=${RSE_LIB_DB_PORT:6432}",
        "--POSTGRES_NAME=cft_task_db",
        "--POSTGRES_USERNAME=postgres",
        "--POSTGRES_PASSWORD=postgres",
        "--POSTGRES_REPLICA_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--POSTGRES_REPLICA_PORT=${RSE_LIB_DB_PORT:6432}",
        "--POSTGRES_REPLICA_NAME=cft_task_db",
        "--REPLICATION_USERNAME=postgres",
        "--REPLICATION_PASSWORD=postgres",
        "--POSTGRES_CLUSTER_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--CCD_URL=http://localhost:4452"
    );

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
