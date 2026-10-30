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
        "--POSTGRES_USERNAME=${RSE_LIB_DB_USERNAME:postgres}",
        "--POSTGRES_PASSWORD=${RSE_LIB_DB_PASSWORD:postgres}",
        "--POSTGRES_REPLICA_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--POSTGRES_REPLICA_PORT=${RSE_LIB_DB_PORT:6432}",
        "--POSTGRES_REPLICA_NAME=cft_task_db",
        "--REPLICATION_USERNAME=${RSE_LIB_DB_USERNAME:postgres}",
        "--REPLICATION_PASSWORD=${RSE_LIB_DB_PASSWORD:postgres}",
        "--POSTGRES_CLUSTER_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--CCD_URL=http://localhost:4452"
    ),
    // HMCTS's Camunda engine, built on the community edition. As in preview, services call its REST API without S2S.
    camundaBpm(Feature.camunda,
        "--server.port=${CAMUNDA_PORT:8999}",
        "--management.server.port=${CAMUNDA_PORT:8999}",
        "--CAMUNDA_DB_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--CAMUNDA_DB_PORT=${RSE_LIB_DB_PORT:6432}",
        "--CAMUNDA_DB_NAME=camunda",
        "--CAMUNDA_DB_USER_NAME=${RSE_LIB_DB_USERNAME:postgres}",
        "--CAMUNDA_DB_PASSWORD=${RSE_LIB_DB_PASSWORD:postgres}",
        // Environments migrate the schema with Flyway in their pipeline; here the engine creates it.
        "--camunda.bpm.database.schema-update=true",
        "--camunda.api.auth.enabled=false",
        "--S2S_URL=http://localhost:${RSE_LIB_S2S_PORT:8489}",
        "--TASK_MANAGEMENT_API_URL=http://localhost:8087",
        // Camunda asks task management to initiate each WA task as it is created, rather than wa-task-monitor
        // looking for new tasks on a schedule.
        "--WA_INITIATE_TASKS_ON_CREATE_ENABLED=true"
    ),
    // Turns CCD case events into Camunda messages that start and cancel tasks.
    waCaseEventHandler(Feature.wa,
        // Pinned, as other services and the runtime call it here.
        "--server.port=8088",
        "--POSTGRES_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--POSTGRES_PORT=${RSE_LIB_DB_PORT:6432}",
        "--POSTGRES_NAME=wa_case_event_messages_db",
        "--POSTGRES_USERNAME=${RSE_LIB_DB_USERNAME:postgres}",
        "--POSTGRES_PASSWORD=${RSE_LIB_DB_PASSWORD:postgres}",
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
        // Pinned, as other services and the runtime call it here.
        "--server.port=8099",
        "--POSTGRES_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--POSTGRES_PORT=${RSE_LIB_DB_PORT:6432}",
        "--POSTGRES_NAME=wa_workflow_api",
        "--POSTGRES_USERNAME=${RSE_LIB_DB_USERNAME:postgres}",
        "--POSTGRES_PASSWORD=${RSE_LIB_DB_PASSWORD:postgres}",
        "--CAMUNDA_URL=" + Wa.CAMUNDA_URL
    ),
    // Runs WA's batch jobs, which cftlib triggers on request rather than on a schedule.
    waTaskMonitor(Feature.wa,
        // Pinned, as other services and the runtime call it here.
        "--server.port=8077",
        "--POSTGRES_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--POSTGRES_PORT=${RSE_LIB_DB_PORT:6432}",
        "--POSTGRES_NAME=cft_task_db",
        "--POSTGRES_USERNAME=${RSE_LIB_DB_USERNAME:postgres}",
        "--POSTGRES_PASSWORD=${RSE_LIB_DB_PASSWORD:postgres}",
        "--CAMUNDA_URL=" + Wa.CAMUNDA_URL,
        "--CCD_URL=http://localhost:4452",
        "--CCD_SEARCH_URL=http://localhost:4452",
        "--OPEN_ID_IDAM_URL=${IDAM_API_URL}",
        "--IDAM_URL=${IDAM_API_URL}",
        "--WA_SYSTEM_USERNAME=" + Wa.SYSTEM_USERNAME,
        "--WA_SYSTEM_PASSWORD=" + Wa.SYSTEM_PASSWORD,
        "--WA_INITIATE_TASKS_ON_CREATE_ENABLED=true"
    ),
    // Takes hearing requests, sends them to HMI through the outbound adapter and publishes hearing updates to the
    // hmc-to-cft topic.
    hmcCftHearingService(Feature.hmc,
        // Pinned, as other services and the runtime call it here.
        "--server.port=4561",
        "--CFT_HEARING_SERVICE_DB_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--CFT_HEARING_SERVICE_DB_PORT=${RSE_LIB_DB_PORT:6432}",
        "--CFT_HEARING_SERVICE_DB_NAME=" + Hmc.DATABASE,
        "--CFT_HEARING_SERVICE_DB_USERNAME=${RSE_LIB_DB_USERNAME:postgres}",
        "--CFT_HEARING_SERVICE_DB_PASSWORD=${RSE_LIB_DB_PASSWORD:postgres}",
        "--HMC_SERVICE_BUS_CONNECTION_STRING=" + Hmc.SERVICE_BUS,
        "--HMC_SERVICE_BUS_TOPIC=" + Hmc.TO_CFT_TOPIC,
        "--HMC_QUEUE_CONNECTION_STRING=" + Hmc.SERVICE_BUS,
        "--HMC_SERVICE_BUS_QUEUE=" + Hmc.FROM_HMI_QUEUE,
        "--HMC_OUTBOUND_QUEUE_CONNECTION_STRING=" + Hmc.SERVICE_BUS,
        "--HMC_OUTBOUND_SERVICE_BUS_QUEUE=" + Hmc.TO_HMI_QUEUE,
        "--IDAM_OIDC_URL=${IDAM_API_URL}",
        // The IDAM client adds /o itself, so HMC's own default, IDAM_OIDC_URL with /o, would double it.
        "--idam.oidc.url=${IDAM_API_URL}",
        "--CCD_DATA_STORE_API_BASE_URL=http://localhost:4452",
        "--CFT_HEARING_SERVICE_S2S_AUTHORISED_SERVICES=" + Hmc.S2S_AUTHORISED_SERVICES,
        "--FH_BASE_URL=" + Hmc.HMI_URL,
        "--FH_GET_TOKEN_URL=" + Hmc.TOKEN_PATH,
        "--HMI_BASE_URL=" + Hmc.HMI_URL,
        // Written by the runtime, since the service cannot read the copy in its jar.
        "--ROOM_ATTRIBUTES_FILE=file:${java.io.tmpdir}/cftlib-hmc-room-attributes.json"
    ),
    // Receives HMI's listings and passes them to the hearing service through the hmc-from-hmi queue.
    hmcHmiInboundAdapter(Feature.hmc,
        // Pinned, as other services and the runtime call it here.
        "--server.port=4559",
        "--CFT_HEARING_SERVICE_URL=http://localhost:4561",
        "--HMC_SERVICE_BUS_CONNECTION_STRING=" + Hmc.SERVICE_BUS,
        "--HMC_SERVICE_BUS_QUEUE=" + Hmc.FROM_HMI_QUEUE,
        "--IDAM_OIDC_URL=${IDAM_API_URL}",
        "--idam.oidc.url=${IDAM_API_URL}",
        // HMI's tokens come from ForgeRock in an environment.
        "--OIDC_ISSUER_FORGEROCK=${IDAM_API_URL}/o",
        "--HMC_SYSTEM_USER_ID=" + Hmc.SYSTEM_USERNAME,
        "--HMC_SYSTEM_USER_PASSWORD=" + Hmc.SYSTEM_PASSWORD
    ),
    // Sends the hearing service's pending requests to HMI, which in cftlib is a stand-in in the runtime.
    hmcHmiOutboundAdapter(Feature.hmc,
        // Pinned, as other services and the runtime call it here.
        "--server.port=4558",
        "--CFT_HEARING_SERVICE_DB_HOST=${RSE_LIB_DB_HOST:localhost}",
        "--CFT_HEARING_SERVICE_DB_PORT=${RSE_LIB_DB_PORT:6432}",
        "--CFT_HEARING_SERVICE_DB_NAME=" + Hmc.DATABASE,
        "--CFT_HEARING_SERVICE_DB_USERNAME=${RSE_LIB_DB_USERNAME:postgres}",
        "--CFT_HEARING_SERVICE_DB_PASSWORD=${RSE_LIB_DB_PASSWORD:postgres}",
        "--HMC_SERVICE_BUS_OUTBOUND_CONNECTION_STRING=" + Hmc.SERVICE_BUS,
        "--HMC_SERVICE_BUS_OUTBOUND_QUEUE=" + Hmc.TO_HMI_QUEUE,
        "--HMC_SERVICE_BUS_INBOUND_CONNECTION_STRING=" + Hmc.SERVICE_BUS,
        "--HMC_SERVICE_BUS_INBOUND_QUEUE=" + Hmc.FROM_HMI_QUEUE,
        "--HMC_SERVICE_BUS_CONNECTION_STRING=" + Hmc.SERVICE_BUS,
        "--HMC_SERVICE_BUS_TOPIC=" + Hmc.TO_CFT_TOPIC,
        "--FH_BASE_URL=" + Hmc.HMI_URL,
        "--FH_GET_TOKEN_URL=" + Hmc.TOKEN_PATH,
        "--HMI_BASE_URL=" + Hmc.HMI_URL,
        // In an environment the adapter looks for pending requests every two minutes, and retries after two more.
        "--PR_PENDING_WAIT_IN_MILLISECS=500",
        "--PR_PENDING_WAIT_INTERVAL=5,SECONDS",
        // The adapter logs every SQL statement and its parameters, and each poll, which at this rate floods the log.
        "--logging.level.org.hibernate.SQL=INFO",
        "--logging.level.org.hibernate.type.descriptor.sql.BasicBinder=INFO",
        "--logging.level.uk.gov.hmcts.reform.hmc.service.PendingRequestServiceImpl=WARN"
    ),
    rseIdamSimulator(
        "--server.port=5062",
        "--simulator.jwt.issuer=${IDAM_SIMULATOR_BASE_URL:http://localhost:5062}/o",
        "--simulator.openid.base-url=${IDAM_SIMULATOR_BASE_URL:http://localhost:5062}",
        "--simulator.openid.base-url-outside-network="
            + "${IDAM_SIMULATOR_BASE_URL_OUTSIDE_NETWORK:http://localhost:5062}"
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

    /**
     * Arguments the service only gets when a feature is on. Task management runs whether or not work allocation does,
     * and only uses cftlib's Camunda and WA system user when it does; otherwise its own configuration applies.
     */
    public List<String> featureArgs(Feature feature) {
        if (this == waTaskManagementApi && feature == Feature.wa) {
            return List.of(
                "--CAMUNDA_URL=" + Wa.CAMUNDA_URL,
                "--OPEN_ID_IDAM_URL=${IDAM_API_URL}",
                "--IDAM_URL=${IDAM_API_URL}",
                "--WA_SYSTEM_USERNAME=" + Wa.SYSTEM_USERNAME,
                "--WA_SYSTEM_PASSWORD=" + Wa.SYSTEM_PASSWORD
            );
        }
        return List.of();
    }

    // Settings the WA services share. The runtime creates the system user when the wa feature is on.
    static final class Wa {
        static final String CAMUNDA_URL = "http://localhost:${CAMUNDA_PORT:8999}/engine-rest";
        static final String SYSTEM_USERNAME = "wa-system-user@cftlib.local";
        static final String SYSTEM_PASSWORD = "password";
    }

    // Settings the HMC services share. The runtime creates the system user and serves HMI when the hmc feature is on.
    static final class Hmc {
        static final String DATABASE = "hmc_cft_hearing_service";
        static final String SERVICE_BUS = "${RSE_LIB_SERVICEBUS_CONNECTION_STRING}";
        static final String TO_HMI_QUEUE = "hmc-to-hmi";
        static final String FROM_HMI_QUEUE = "hmc-from-hmi";
        static final String TO_CFT_TOPIC = "hmc-to-cft";
        static final String HMI_URL = "http://localhost:${RSE_LIB_S2S_PORT:8489}/hmi";
        static final String TOKEN_PATH = "/oauth2/token";
        static final String SYSTEM_USERNAME = "hmc-system-user@cftlib.local";
        static final String SYSTEM_PASSWORD = "password";
        // The services that call HMC in an environment, which a build can replace with RSE_LIB_HMC_S2S_SERVICES.
        static final String S2S_AUTHORISED_SERVICES = "${RSE_LIB_HMC_S2S_SERVICES:xui_webapp,hmc_hmi_inbound_adapter,"
            + "civil_service,et_cos,et_hearings_api,ia_hearings_api,prl_cos_api,prl-cos-api,sscs,fis_hmc_api,pcs_api,"
            + "sptribs_case_api,finrem_case_orchestration,hmc-admin-ui}";
    }

    public String id() {
        return CaseFormat.LOWER_CAMEL
                .to(CaseFormat.LOWER_HYPHEN, toString())
                .replace("_", "-");
    }
}
