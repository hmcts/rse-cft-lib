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
        "--CCD_URL=http://localhost:4452",
        "--CAMUNDA_URL=" + Wa.CAMUNDA_URL,
        "--OPEN_ID_IDAM_URL=${IDAM_API_URL}",
        "--IDAM_URL=${IDAM_API_URL}",
        "--WA_SYSTEM_USERNAME=" + Wa.SYSTEM_USERNAME,
        "--WA_SYSTEM_PASSWORD=" + Wa.SYSTEM_PASSWORD
    ),
    // Turns CCD case events into Camunda messages that start and cancel tasks.
    waCaseEventHandler(Feature.wa,
        "--POSTGRES_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--POSTGRES_PORT=${RSE_LIB_DB_PORT:6432}",
        "--POSTGRES_NAME=wa_case_event_messages_db",
        "--POSTGRES_USERNAME=postgres",
        "--POSTGRES_PASSWORD=postgres",
        "--CCD_URL=http://localhost:4452",
        "--CCD_SEARCH_URL=http://localhost:4452",
        "--OPEN_ID_IDAM_URL=${IDAM_API_URL}",
        "--IDAM_URL=${IDAM_API_URL}",
        "--WA_SYSTEM_USERNAME=" + Wa.SYSTEM_USERNAME,
        "--WA_SYSTEM_PASSWORD=" + Wa.SYSTEM_PASSWORD,
        // cftlib posts case events to /messages rather than through Service Bus, so the consumers stay off.
        "--AZURE_SERVICE_BUS_CONNECTION_STRING=unused",
        "--AZURE_SERVICE_BUS_DLQ_FEATURE_TOGGLE=false",
        // Flyway holds a transaction open on its lock connection, which a CREATE INDEX CONCURRENTLY waits on forever.
        "--FLYWAY_CONCURRENTLY="
    ),
    // Evaluates DMNs and sends messages to Camunda, and works the standalone task process's external tasks.
    waWorkflowApi(Feature.wa,
        "--POSTGRES_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--POSTGRES_PORT=${RSE_LIB_DB_PORT:6432}",
        "--POSTGRES_NAME=wa_workflow_api",
        "--POSTGRES_USERNAME=postgres",
        "--POSTGRES_PASSWORD=postgres",
        "--CAMUNDA_URL=" + Wa.CAMUNDA_URL
    ),
    // Runs WA's batch jobs, which cftlib triggers on request rather than on a schedule.
    waTaskMonitor(Feature.wa,
        "--POSTGRES_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--POSTGRES_PORT=${RSE_LIB_DB_PORT:6432}",
        "--POSTGRES_NAME=cft_task_db",
        "--POSTGRES_USERNAME=postgres",
        "--POSTGRES_PASSWORD=postgres",
        "--CAMUNDA_URL=" + Wa.CAMUNDA_URL,
        "--CCD_URL=http://localhost:4452",
        "--CCD_SEARCH_URL=http://localhost:4452",
        "--OPEN_ID_IDAM_URL=${IDAM_API_URL}",
        "--IDAM_URL=${IDAM_API_URL}",
        "--WA_SYSTEM_USERNAME=" + Wa.SYSTEM_USERNAME,
        "--WA_SYSTEM_PASSWORD=" + Wa.SYSTEM_PASSWORD,
        "--WA_INITIATE_TASKS_ON_CREATE_ENABLED=true"
    );

    public final List<String> args;
    // The feature that runs the service, or null for a service that always runs.
    public final Feature feature;

    Service() {
        this(null, new String[0]);
    }

    Service(String... args) {
        this(null, args);
    }

    Service(Feature feature, String... args) {
        this.feature = feature;
        this.args = List.of(args);
    }

    // Settings the WA services share. The runtime creates the system user when the wa feature is on.
    static final class Wa {
        static final String CAMUNDA_URL = "http://localhost:${CAMUNDA_PORT:8999}/engine-rest";
        static final String SYSTEM_USERNAME = "wa-system-user@cftlib.local";
        static final String SYSTEM_PASSWORD = "password";
    }

    public String id() {
        return CaseFormat.LOWER_CAMEL
                .to(CaseFormat.LOWER_HYPHEN, toString())
                .replace("_", "-");
    }
}
