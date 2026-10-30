package uk.gov.hmcts.libconsumer;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.google.gson.Gson;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

/**
 * Hearings with the hmc feature: a hearing requested from hmc-cft-hearing-service goes to the HMI stand-in through
 * the outbound adapter, and each change to it reaches services on the hmc-to-cft topic, as it would in an
 * environment.
 */
// The same context as LibConsumerApplicationTests, which Spring reuses rather than starting another on the same port.
// The feature runs containers, so is off when the test-project runs without docker.
@EnabledIfEnvironmentVariable(named = "RSE_LIB_FEATURES", matches = "(.*,)?hmc(,.*)?")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HmcTests extends CftlibTest {

    private static final String HEARING_SERVICE = "http://localhost:4561";
    private static final Duration WAIT = Duration.ofMinutes(1);
    // A caseworker created by CFTLibConfig. HMC looks its callers up in IDAM, so they need real tokens.
    private static final String USER = "a@b.com";

    private final HttpClient http = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    // Hearing updates for every service, from the subscription in src/cftlib/servicebus.json.
    private ServiceBusReceiverClient updates;
    private final List<Map> received = new ArrayList<>();
    private String token;

    @BeforeAll
    void subscribe() {
        token = idamToken(USER);
        updates = new ServiceBusClientBuilder()
            .connectionString(System.getProperty("RSE_LIB_SERVICEBUS_CONNECTION_STRING"))
            .receiver()
            .topicName("hmc-to-cft")
            .subscriptionName("cftlib-hearing-updates")
            .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
            .buildClient();

        // HMC checks that the user has an organisational hearing role for the case's jurisdiction. The IDAM simulator
        // derives a user's id from their email.
        cftlib().configureRoleAssignments(gson.toJson(List.of(Map.of(
            "id", UUID.nameUUIDFromBytes(USER.getBytes(StandardCharsets.UTF_8)).toString(),
            "overrideAll", false,
            "roleAssignments", List.of(Map.of(
                "roleType", "ORGANISATION",
                "roleName", "hearing-manager",
                "grantType", "STANDARD",
                "roleCategory", "LEGAL_OPERATIONS",
                "classification", "PUBLIC",
                "readOnly", false,
                "attributes", Map.of("jurisdiction", "DIVORCE", "caseType", "NFD"),
                "authorisations", List.of()
            ))
        ))));
    }

    @AfterAll
    void close() {
        updates.close();
    }

    @Test
    void listsAHearingThatHmiAccepts() {
        var hearingId = requestHearing(createCase());

        // HMI accepted the request, so the hearing awaits listing.
        awaitStatus(hearingId, "AWAITING_LISTING");
        assertThat(cftlib().getHmiRequests(hearingId), contains(startsWith("POST ")));

        cftlib().sendHmiListing(hearingId, listing());

        var update = awaitStatus(hearingId, "LISTED");
        assertThat(update.get("hearingListingStatus"), equalTo("FIXED"));
    }

    @Test
    void putsAHearingThatHmiRejectsInException() {
        var caseReference = createCase();
        cftlib().rejectNextHmiRequest(caseReference, 2000, "cftlib rejected the hearing request");

        var hearingId = requestHearing(caseReference);

        awaitStatus(hearingId, "EXCEPTION");
        assertThat(cftlib().getHmiRequests(hearingId), hasSize(1));
    }

    @SneakyThrows
    private String createCase() {
        var trigger = send(HttpRequest.newBuilder(URI.create(
                "http://localhost:4452/data/internal/case-types/NFD/event-triggers/create-test-application"))
            .header("experimental", "true")
            .header("Accept",
                "application/vnd.uk.gov.hmcts.ccd-data-store-api.ui-start-case-trigger.v2+json;charset=UTF-8")
            .GET(), 200);
        var body = Map.of(
            "data", Map.of(
                "applicationType", "soleApplication",
                "applicant1SolicitorRepresented", "No",
                "applicant2SolicitorRepresented", "No",
                "stateToTransitionApplicationTo", "Draft"
            ),
            "event", Map.of("id", "create-test-application", "summary", "", "description", ""),
            "event_token", trigger.get("event_token"),
            "ignore_warning", false
        );
        var created = send(HttpRequest.newBuilder(URI.create("http://localhost:4452/data/case-types/NFD/cases"))
            .header("experimental", "true")
            .header("Accept", "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-case.v2+json;charset=UTF-8")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))), 201);
        return id(created.get("id"));
    }

    private String requestHearing(String caseReference) {
        var request = """
            {
              "requestDetails": {"requestTimeStamp": "%s"},
              "hearingDetails": {
                "hearingChannels": ["INTER"],
                "autolistFlag": false,
                "hearingType": "ABA1-HEA",
                "isAPanelFlag": false,
                "hearingWindow": {"dateRangeStart": "%s", "dateRangeEnd": "%s"},
                "duration": 60,
                "hearingPriorityType": "Standard",
                "hearingLocations": [{"locationType": "court", "locationId": "20262"}],
                "panelRequirements": {}
              },
              "caseDetails": {
                "hmctsServiceCode": "ABA1",
                "caseRef": "%s",
                "requestTimeStamp": "%s",
                "caseDeepLink": "http://localhost:3000/cases/case-details/%s",
                "hmctsInternalCaseName": "cftlib hearing",
                "publicCaseName": "cftlib hearing",
                "caseCategories": [
                  {"categoryType": "caseType", "categoryValue": "ABA1-001"},
                  {"categoryType": "caseSubType", "categoryValue": "ABA1-001", "categoryParent": "ABA1-001"}
                ],
                "caseManagementLocationCode": "20262",
                "caserestrictedFlag": false,
                "caseSLAStartDate": "%s"
              },
              "partyDetails": [{
                "partyID": "P1",
                "partyType": "IND",
                "partyRole": "APPL",
                "individualDetails": {"title": "Mr", "firstName": "Joe", "lastName": "Bloggs"}
              }]
            }
            """.formatted(LocalDateTime.now(), LocalDate.now().plusDays(7), LocalDate.now().plusDays(60),
                caseReference, LocalDateTime.now(), caseReference, LocalDate.now());
        var response = send(HttpRequest.newBuilder(URI.create(HEARING_SERVICE + "/hearing"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(request)), 201);
        return id(response.get("hearingRequestID"));
    }

    private String listing() {
        var start = LocalDate.now().plusDays(14).atTime(10, 0);
        return """
            {
              "hearingResponse": {
                "meta": {"timestamp": "%s", "transactionIdCaseHQ": "cftlib-listing"},
                "hearing": {
                  "listingRequestId": "cftlib-listing",
                  "hearingCaseVersionId": 1,
                  "hearingCaseIdHMCTS": "cftlib-listing",
                  "hearingCaseStatus": {"code": "100", "description": "Listed"},
                  "hearingStatus": {"code": "FIXED", "description": "Fixed"},
                  "hearingIdCaseHQ": "cftlib-listing",
                  "hearingStartTime": "%s",
                  "hearingEndTime": "%s",
                  "hearingPrivate": false,
                  "hearingRisk": false,
                  "hearingTranslatorRequired": false,
                  "hearingCreatedDate": "%s",
                  "hearingCreatedBy": "cftlib",
                  "hearingVenue": {
                    "locationIdCaseHQ": "20262",
                    "locationName": "cftlib court",
                    "locationReferences": [{"key": "EPIMS", "value": "20262"}]
                  },
                  "hearingRoom": {"locationIdCaseHQ": "1", "locationName": "Room 1"}
                }
              }
            }
            """.formatted(LocalDateTime.now(), start, start.plusHours(1), LocalDateTime.now());
    }

    /**
     * The update for the hearing with this status, from the hmc-to-cft topic.
     */
    private Map awaitStatus(String hearingId, String status) {
        return await().atMost(WAIT).pollInterval(Duration.ZERO).until(() -> {
            updates.receiveMessages(10, Duration.ofSeconds(2))
                .forEach(m -> received.add(gson.fromJson(m.getBody().toString(), Map.class)));
            return received.stream()
                .filter(m -> hearingId.equals(m.get("hearingID")))
                .map(m -> (Map) m.get("hearingUpdate"))
                .filter(u -> status.equals(u.get("HMCStatus")))
                .findFirst()
                .orElse(null);
        }, u -> u != null);
    }

    @SneakyThrows
    private String idamToken(String email) {
        var form = "grant_type=password&client_id=cftlib&client_secret=cftlib&scope=openid%20profile%20roles"
            + "&username=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=password";
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:5062/o/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), Map.class).get("access_token").toString();
    }

    // Gson reads a JSON number as a double, which would print a long id in scientific notation.
    private static String id(Object value) {
        return value instanceof Number n ? String.valueOf(n.longValue()) : value.toString();
    }

    @SneakyThrows
    private Map send(HttpRequest.Builder request, int expectedStatus) {
        var response = http.send(request
                .header("Authorization", "Bearer " + token)
                .header("ServiceAuthorization", generateDummyS2SToken("xui_webapp"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.uri() + " returned " + response.body(), response.statusCode(), equalTo(expectedStatus));
        return gson.fromJson(response.body(), Map.class);
    }
}
