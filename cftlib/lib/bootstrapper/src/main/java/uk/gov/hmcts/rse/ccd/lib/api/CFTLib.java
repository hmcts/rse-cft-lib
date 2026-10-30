package uk.gov.hmcts.rse.ccd.lib.api;

import uk.gov.hmcts.rse.ccd.lib.Database;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

public interface CFTLib {
    /**
     * Create an IDAM user.
     * Password 'password'.
     */
    void createIdamUser(String email, String... roles);

    /**
     * Create a CCD User Profile.
     */
    void createProfile(String id, String jurisdiction, String caseType, String state);

    /**
     * Create roles in CCD Definition store.
     */
    void createRoles(String... roles);

    /**
     * Configure the AM role assignment service
     * For format see https://github.com/hmcts/rse-cft-lib/blob/main/test-project/src/cftlib/resources/cftlib-am-role-assignments.json
     */
    void configureRoleAssignments(String json);

    /**
     * Import a CCD definition spreadsheet.
     *
     * @param def A Microsoft xlsx file
     */
    void importDefinition(byte[] def);

    /**
     * Import a CCD definition spreadsheet.
     *
     * @param def A Microsoft xlsx file
     */
    void importDefinition(File def);

    /**
     * Import a CCD definition from json in the format used by the ccd definition processor.
     *
     * @param defFolder folder containing json ccd definition
     */
    void importJsonDefinition(File defFolder);

    /**
     * Import JSON with explicit substitutions, an optional spreadsheet template and filename exclusions.
     * No environment configuration or template is discovered automatically.
     *
     * @param defFolder folder containing JSON CCD definition
     * @param template spreadsheet supplying sheet names, columns and defaults, or null
     * @param substitutions values to replace ${NAME} placeholders, including JSON fragments
     * @param excludedFilenamePatterns filename glob patterns to omit
     */
    void importJsonDefinition(File defFolder, File template, Map<String, String> substitutions,
                              String... excludedFilenamePatterns);

    /**
     * Obtain connections to common component databases.
     */
    Connection getConnection(Database database);

    /**
     * Private method invoked automatically to create CCD's global search elasticsearch index.
     */
    void createGlobalSearchIndex();

    /**
     * Deploy BPMN and DMN files to the Camunda engine of the camunda feature, waiting for it to start.
     *
     * @param tenantId the Camunda tenant, such as a service's jurisdiction in lower case, or null for none
     */
    void importCamundaDefinitions(String tenantId, File... files);

    /**
     * With the wa feature, deliver every new CCD case event to work allocation now, rather than within the next
     * second, and return once wa-case-event-handler has handled them.
     */
    void deliverCaseEvents();

    /**
     * With the wa feature, run one of wa-task-monitor's jobs, such as TERMINATION, which in an environment run on a
     * schedule.
     */
    void runWaTaskMonitorJob(String jobName);

    /**
     * With the hmc feature, the requests HMC's outbound adapter has sent the HMI stand-in for a hearing, oldest
     * first, each as its HTTP method and JSON body, such as {@code POST {...}}.
     */
    List<String> getHmiRequests(String hearingId);

    /**
     * With the hmc feature, have the HMI stand-in reject the next hearing request it receives with a 400 and this
     * error, as HMI does a request it cannot list. HMC then puts the hearing in EXCEPTION.
     */
    void rejectNextHmiRequest(int errorCode, String description);

    /**
     * With the hmc feature, have the HMI stand-in reject the next hearing request for this case with a 400 and this
     * error. Unlike {@link #rejectNextHmiRequest(int, String)}, a request for another case, such as a retry, cannot
     * take the rejection.
     */
    void rejectNextHmiRequest(String caseReference, int errorCode, String description);

    /**
     * With the hmc feature, send HMI's response to a hearing request, such as its listing, to the inbound adapter's
     * PUT /listings/{hearingId}, as HMI does.
     *
     * @param body a HearingDetailsRequest: {@code {"hearingResponse": {"meta": ..., "hearing": ...}}}
     */
    void sendHmiListing(String hearingId, String body);

    String generateDummyS2SToken(String serviceName);

    String buildJwt();

    void dumpDefinitionSnapshots();
}
