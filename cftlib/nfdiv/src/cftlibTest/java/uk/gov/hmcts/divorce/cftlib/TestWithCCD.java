package uk.gov.hmcts.divorce.cftlib;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.sow014.nfd.FailingSubmittedCallback;
import uk.gov.hmcts.divorce.sow014.nfd.PublishedEvent;
import uk.gov.hmcts.divorce.sow014.nfd.ReturnErrorWhenCreateTestCase;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.rse.ccd.lib.Database;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class TestWithCCD extends CftlibTest {

    @Autowired
    private IdamClient idam;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private CoreCaseDataApi ccdApi;

    @Autowired
    NamedParameterJdbcTemplate db;

    private long firstEventId;

    @Order(1)
    @Test
    public void caseCreation() throws Exception {
        var start = ccdApi.startCase(getAuthorisation("TEST_SOLICITOR@mailinator.com"),
            getServiceAuth(),
            "NFD",
            "create-test-application");

        var startData = mapper.readValue(mapper.writeValueAsString(start.getCaseDetails().getData()), CaseData.class);
        assertThat(startData.getSetInAboutToStart(), equalTo("My custom value"));

        start.getCaseDetails();
        var token = start.getToken();

        var body = Map.of(
            "data", Map.of(
                "applicationType", "soleApplication",
                "applicant1SolicitorRepresented", "No",
                "applicant2SolicitorRepresented", "No"
                // applicant2@gmail.com  =  6e508b49-1fa8-3d3c-8b53-ec466637315b
            ),
            "event", Map.of(
                "id", "create-test-application",
                "summary", "",
                "description", ""
            ),
            "event_token", token,
            "ignore_warning", false,
            "supplementary_data_request", Map.of(
            "$set", Map.of(
                "orgs_assigned_users.organisationA", 22,
                    "baz", "qux"
                ),
            "$inc", Map.of(
                "orgs_assigned_users.organisationB", -4,
                    "foo", 5
                )
            )
        );

        var createCase =
            buildRequest("TEST_SOLICITOR@mailinator.com",
                "http://localhost:4452/data/case-types/NFD/cases?ignore-warning=false", HttpPost::new);
        createCase.addHeader("experimental", "true");
        createCase.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-case.v2+json;charset=UTF-8");

        createCase.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(createCase);
        var r = new Gson().fromJson(EntityUtils.toString(response.getEntity()), Map.class);
        caseRef = Long.parseLong((String) r.get("id"));
        assertThat(response.getStatusLine().getStatusCode(), equalTo(201));
        assertThat(r.get("state"), equalTo("Submitted"));

        // Check we can load the case
        var c = ccdApi.getCase(getAuthorisation("TEST_SOLICITOR@mailinator.com"), getServiceAuth(), String.valueOf(caseRef));
        assertThat(c.getState(), equalTo("Submitted"));
        assertThat(c.getLastModified(), greaterThan(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)));
        var caseData = mapper.readValue(mapper.writeValueAsString(c.getData()), CaseData.class);
        assertThat(caseData.getApplicant1().getFirstName(), equalTo("app1_first_name"));
        assertThat(caseData.getApplicant2().getFirstName(), equalTo("app2_first_name"));
    }

    private long caseRef;
    @Order(2)
    @Test
    public void addNotes() throws Exception {

        addNote();
        addNote();

        var get =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef, HttpGet::new);
        get.addHeader("experimental", "true");
        get.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.case.v2+json;charset=UTF-8");

        var response = HttpClientBuilder.create().build().execute(get);
        var result = mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        var data = (Map) result.get("data");
        var caseData = mapper.readValue(mapper.writeValueAsString(data), CaseData.class);
        assertThat(caseData.getNotes().size(), equalTo(2));
        assertThat(caseData.getNotes().get(0).getValue().getNote(), equalTo("Test!"));
    }

    @Order(3)
    @Test
    public void getEventHistory() throws Exception {
        var get =
                buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                        "http://localhost:4452/cases/" + caseRef + "/events", HttpGet::new);
        get.addHeader("experimental", "true");
        get.addHeader("Accept",
                "application/vnd.uk.gov.hmcts.ccd-data-store-api.case-events.v2+json;charset=UTF-8");

        var response = HttpClientBuilder.create().build().execute(get);
        System.out.println(response.getEntity().getContent());
        Map<String, Object> result
                = mapper.readValue(EntityUtils.toString(response.getEntity()), new TypeReference<>() {});
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        var auditEvents = (List) result.get("auditEvents");
        assertThat(auditEvents.size(), equalTo(3));
        var eventData = ((Map)auditEvents.get(0)).get("data");
        var caseData = mapper.readValue(mapper.writeValueAsString(eventData), CaseData.class);
        assertThat(caseData.getNotes().size(), equalTo(2));
        var firstEvent = (Map) auditEvents.getLast();
        // First event should be in the 'Holding' state
        assertThat(firstEvent.get("state_id"), equalTo("Submitted"));
        assertThat(firstEvent.get("state_name"), equalTo("Submitted"));
    }

    @Order(4)
    @Test
    public void testAddNoteRunsConcurrently() throws Exception {
        var firstEvent = ccdApi.startEvent(
            getAuthorisation("TEST_CASE_WORKER_USER@mailinator.com"),
            getServiceAuth(), String.valueOf(caseRef), "caseworker-add-note").getToken();

        var body = Map.of(
            "event_data", Map.of(
                "note", "Test!"
            ),
            "event", Map.of(
                "id", "caseworker-add-note",
                "summary", "summary",
                "description", "description"
            ),
            "event_token", firstEvent,
            "ignore_warning", false
        );

        // Concurrent change to case notes should be allowed without raising a conflict
        addNote();

        var e =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef + "/events", HttpPost::new);
        e.addHeader("experimental", "true");
        e.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-event.v2+json;charset=UTF-8");

        e.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(e);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(201));
    }

    @Order(5)
    @Test
    public void testOptimisticLockOnJsonBlob() throws Exception {
        var firstEvent = ccdApi.startEvent(
            getAuthorisation("TEST_CASE_WORKER_USER@mailinator.com"),
            getServiceAuth(), String.valueOf(caseRef), "caseworker-update-due-date").getToken();

        var body = Map.of(
            "data", Map.of(
                "dueDate", "2020-01-01"
            ),
            "event", Map.of(
                "id", "caseworker-update-due-date",
                "summary", "summary",
                "description", "description"
            ),
            "event_token", firstEvent,
            "ignore_warning", false
        );

        // Concurrent change to json blob should be rejected
        updateDueDate();

        var e =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef + "/events", HttpPost::new);
        e.addHeader("experimental", "true");
        e.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-event.v2+json;charset=UTF-8");

        e.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(e);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(409));
    }

    @Order(6)
    @SneakyThrows
    @Test
    void searchCases() {
        // Give some time to index the case created by the previous test
        await()
            .timeout(Duration.ofSeconds(10))
            .ignoreExceptions()
            .until(this::caseAppearsInSearch);
    }

    @Test
    @Order(7)
    void shouldCreateSupplementaryDataWhenNotExists() throws Exception {
        final String url = "http://localhost:4452/cases/" + caseRef + "/supplementary-data";
        var body = """
            {
              "supplementary_data_updates": {
                "$set": {
                  "orgs_assigned_users.organisationA": 22,
                  "baz": "qux"
                },
                "$inc": {
                  "orgs_assigned_users.organisationB": -4,
                  "foo": 5
                }
              }
            }""";

        var request = buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
            url,
            HttpPost::new);

        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

        var response = HttpClientBuilder.create().build().execute(request);

        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        var result = mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
        var data = (Map) result.get("supplementary_data");
        assertThat(data.get("orgs_assigned_users.organisationA"), equalTo(22));
        assertThat(data.get("foo"), equalTo(10));
        assertThat(data.get("orgs_assigned_users.organisationB"), equalTo(-8));
        assertThat(data.get("baz"), equalTo("qux"));
    }

    @Test
    @Order(8)
    void shouldUpdateSupplementaryData() throws Exception {
        final String url = "http://localhost:4452/cases/" + caseRef + "/supplementary-data";
        var body = """
            {
              "supplementary_data_updates": {
                "$set": {
                  "orgs_assigned_users.organisationA": 21,
                  "foo": 8
                },
                "$inc": {
                  "orgs_assigned_users.organisationB": -4
                }
              }
            }""";

        var request = buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
            url,
            HttpPost::new);

        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

        var response = HttpClientBuilder.create().build().execute(request);

        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        var result = mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
        var data = (Map) result.get("supplementary_data");
        assertThat(data.get("orgs_assigned_users.organisationA"), equalTo(21));
        assertThat(data.get("foo"), equalTo(8));
        assertThat(data.get("orgs_assigned_users.organisationB"), equalTo(-12));
    }

    @Test
    @Order(9)
    @SneakyThrows
    void fetchesSupplementaryData() {
        final String url = "http://localhost:4452/internal/cases/" + caseRef + "/event-triggers/caseworker-add-note";

        var request = buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
            url,
            HttpGet::new);

        request.addHeader("experimental", "true");
        request.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.ui-start-event-trigger.v2+json;charset=UTF-8");

        var response = HttpClientBuilder.create().build().execute(request);

        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        var result = mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
        var supplementaryData = (Map) result.get("supplementary_data");

        assertNotNull(supplementaryData, "Supplementary data should not be null");

        var orgsAssignedUsers = (Map) supplementaryData.get("orgs_assigned_users");
        assertThat(orgsAssignedUsers.get("organisationA"), equalTo(21));
        // Should have been incremented by -4 three times.
        assertThat(orgsAssignedUsers.get("organisationB"), equalTo(-12));
        assertThat(supplementaryData.get("foo"), equalTo(8));
        assertThat(supplementaryData.get("baz"), equalTo("qux"));
    }

    @Test
    @Order(11)
    @SneakyThrows
    void testUpdateFailsForUnsupportedOperator() {
        log.info("Testing failure for an unsupported operator on case {}", caseRef);
        final String url = "http://localhost:4452/cases/" + caseRef + "/supplementary-data";
        // "$push" is not a supported operator according to the LLD.
        var body = """
            {
              "supplementary_data_updates": {
                "$push": {
                  "someArray": "value"
                }
              }
            }""";

        var request = buildRequest("TEST_CASE_WORKER_USER@mailinator.com", url, HttpPost::new);
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(request);

        assertThat("Response code should be 400 Bad Request for unsupported operator",
            response.getStatusLine().getStatusCode(), equalTo(400));
    }


    @Order(13)
    @Test
    public void testEventSubmissionIsIdempotent() throws Exception {
        var firstEvent = ccdApi.startEvent(
            getAuthorisation("TEST_CASE_WORKER_USER@mailinator.com"),
            getServiceAuth(), String.valueOf(caseRef), "caseworker-add-note").getToken();

        var body = Map.of(
            "event_data", Map.of(
                "note", "Test!"
            ),
            "event", Map.of(
                "id", "caseworker-add-note",
                "summary", "summary",
                "description", "description"
            ),
            "event_token", firstEvent,
            "ignore_warning", false
        );

        var e =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef + "/events", HttpPost::new);
        e.addHeader("experimental", "true");
        e.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-event.v2+json;charset=UTF-8");

        e.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        String sql = "SELECT count(*) FROM case_notes";
        Integer initialCount = db.queryForObject(sql, Map.of(), Integer.class);
        var response = HttpClientBuilder.create().build().execute(e);
        // Resubmit the same event a second time which should have no effect.
        HttpClientBuilder.create().build().execute(e);
        Integer thirdCount = db.queryForObject(sql, Map.of(), Integer.class);
        assertThat("The note count should increment by exactly one.", thirdCount, equalTo(initialCount + 1));
    }

    @Order(14)
    @Test
    public void getEventHistory_ShouldReturnAllEventsWithCorrectState() throws Exception {
        String url = String.format("http://localhost:4452/internal/cases/%s", caseRef);

        var get = buildRequest("TEST_CASE_WORKER_USER@mailinator.com", url, HttpGet::new);
        get.addHeader("experimental", "true");
        get.addHeader("Accept", "application/vnd.uk.gov.hmcts.ccd-data-store-api.ui-case-view.v2+json;charset=UTF-8");

        var response = HttpClientBuilder.create().build().execute(get);

        Map<String, Object> result = mapper.readValue(EntityUtils.toString(response.getEntity()), new TypeReference<>() {});

        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        List auditEvents = (List) result.get("events");
        assertThat("Incorrect number of events found", auditEvents.size(), equalTo(7));

        // Get the oldest event (the creation event), which is the last in the list
        var firstEvent = (Map) auditEvents.get(auditEvents.size() - 1);
        assertThat("First event should be in the 'Submitted' state", firstEvent.get("state_id"), equalTo("Submitted"));
        assertThat("First event should have the state name 'Submitted'", firstEvent.get("state_name"), equalTo("Submitted"));

        this.firstEventId = Long.valueOf(firstEvent.get("id").toString());
    }

    @Order(15)
    @Test
    public void getCaseEventById() throws Exception {
        // 1. Build the request URL with the stored caseRef and firstEventId
        final String url = "http://localhost:4452/internal/cases/" + caseRef + "/events/" + firstEventId;
        var get = buildRequest("TEST_CASE_WORKER_USER@mailinator.com", url, HttpGet::new);

        // 2. Add required headers
        get.addHeader("experimental", "true");
        get.addHeader("Accept", "application/vnd.uk.gov.hmcts.ccd-data-store-api.ui-event-view.v2+json;charset=UTF-8");

        // 3. Execute the request
        var response = HttpClientBuilder.create().build().execute(get);
        String responseBody = EntityUtils.toString(response.getEntity());

        // 4. Assert status code
        assertEquals(200, response.getStatusLine().getStatusCode(), "Expected HTTP 200 OK");

        // 5. Deserialize and assert the response body
        Map<String, Object> result = mapper.readValue(responseBody, new TypeReference<>() {});
        assertThat(result.get("case_id"), equalTo(String.valueOf(caseRef)));

        Map event = (Map) result.get("event");
        assertNotNull(event, "Event object should not be null");

        // The 'id' in the event object is the event's internal ID, which is what we queried for
        assertThat(((Number)event.get("id")).longValue(), equalTo(firstEventId));
        // The 'event_id' is the string identifier from the definition
        assertThat(event.get("event_id"), equalTo("create-test-application"));
        assertThat(event.get("event_name"), equalTo("Create test case"));
    }

    @SneakyThrows
    @Order(16)
    @Test
    public void testSubmittedCallback() {
        var token = ccdApi.startEvent(
            getAuthorisation("TEST_CASE_WORKER_USER@mailinator.com"),
            getServiceAuth(), String.valueOf(caseRef), FailingSubmittedCallback.class.getSimpleName()).getToken();

        var body = Map.of(
            "data", Map.of(
                "note", "Test!"
            ),
            "event", Map.of(
                "id", FailingSubmittedCallback.class.getSimpleName(),
                "summary", "summary",
                "description", "description"
            ),
            "event_token", token,
            "ignore_warning", false
        );

        var e =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef + "/events", HttpPost::new);
        e.addHeader("experimental", "true");
        e.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-event.v2+json;charset=UTF-8");

        e.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(e);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(201));
        assertThat(FailingSubmittedCallback.callbackAttempts, equalTo(3));
    }

    @SneakyThrows
    @Order(17)
    @Test
    public void testPublishingToMessageOutbox() {
        var token = ccdApi.startEvent(
            getAuthorisation("TEST_CASE_WORKER_USER@mailinator.com"),
            getServiceAuth(), String.valueOf(caseRef), PublishedEvent.class.getSimpleName()).getToken();

        var body = Map.of(
            "data", Map.of(
                "note", "Test!"
            ),
            "event", Map.of(
                "id", PublishedEvent.class.getSimpleName(),
                "summary", "summary",
                "description", "description"
            ),
            "event_token", token,
            "ignore_warning", false
        );

        var e =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef + "/events", HttpPost::new);
        e.addHeader("experimental", "true");
        e.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-event.v2+json;charset=UTF-8");

        e.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));

        String sql = "SELECT count(*) FROM ccd.message_queue_candidates";
        Integer initialCount = db.queryForObject(sql, Map.of(), Integer.class);

        var response = HttpClientBuilder.create().build().execute(e);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(201));

        Integer secondCount = db.queryForObject(sql, Map.of(), Integer.class);
        assertThat(secondCount - initialCount, equalTo(1));

        String noteCheck = """
            SELECT message_information->'AdditionalData'->'Data'->>'note'
             FROM ccd.message_queue_candidates
             WHERE reference = :caseReference 
             """;

        String retrievedNote = db.queryForObject(noteCheck, Map.of("caseReference", caseRef), String.class);
        assertThat(retrievedNote, equalTo("Test!"));

        // Verify the EventTimeStamp from the JSON blob
        String timestampCheckSql = """
            SELECT message_information->>'EventTimeStamp'
             FROM ccd.message_queue_candidates 
             WHERE reference = :caseReference """;
        String retrievedTimestampStr = db.queryForObject(timestampCheckSql, Map.of("caseReference", caseRef), String.class);
        assertThat(retrievedTimestampStr, is(notNullValue()));
        // Validate it's a parsable timestamp and it is recent
        LocalDateTime eventTimestamp = LocalDateTime.parse(retrievedTimestampStr);
        assertThat(eventTimestamp, is(greaterThan(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))));
    }

    @SneakyThrows
    @Order(18)
    @Test
    public void testReturnErrorWhenCreateTestCase() {
        log.info("Testing that a case create that returns errors is rolled back in CCD");

        var initialPointerCount = getDataStoreCasePointerCount();

        // 1. Get initial case count from CCD database
        String sql = "SELECT count(*) FROM ccd.case_data";
        Integer initialCaseCount = db.queryForObject(sql, Map.of(), Integer.class);
        assertNotNull(initialCaseCount);

        // 2. Start the event to get a valid event token
        var start = ccdApi.startCase(getAuthorisation("TEST_SOLICITOR@mailinator.com"),
            getServiceAuth(),
            "NFD",
            ReturnErrorWhenCreateTestCase.class.getSimpleName());
        var token = start.getToken();

        // 3. Construct the request body for the event that is designed to fail
        var body = Map.of(
            "data", Map.of(
                "applicationType", "soleApplication" // Mandatory field as per event definition
            ),
            "event", Map.of(
                "id", ReturnErrorWhenCreateTestCase.class.getSimpleName(),
                "summary", "Test summary for failing case creation",
                "description", "Testing rollback of case pointer"
            ),
            "event_token", token,
            "ignore_warning", false
        );

        // 4. Build and execute the POST request to submit the case
        var createCaseRequest =
            buildRequest("TEST_SOLICITOR@mailinator.com",
                "http://localhost:4452/data/case-types/NFD/cases?ignore-warning=false", HttpPost::new);
        createCaseRequest.addHeader("experimental", "true");
        createCaseRequest.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-case.v2+json;charset=UTF-8");
        createCaseRequest.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(createCaseRequest);

        // 5. Assert the response indicates failure with the correct error
        assertThat("Expected HTTP 422 Unprocessable Entity",
            response.getStatusLine().getStatusCode(), equalTo(422));

        Integer finalCaseCount = db.queryForObject(sql, Map.of(), Integer.class);
        assertThat("Case count should not increment on failed submission", finalCaseCount, equalTo(initialCaseCount));

        var finalPointerCount = getDataStoreCasePointerCount();
        assertThat("Case pointer count should not increment on failed submission", finalPointerCount, equalTo(initialPointerCount));
    }

    @SneakyThrows
    private int getDataStoreCasePointerCount() {
        String sql = "SELECT COUNT(*) FROM case_data";
        int caseCount = 0;

        try (Connection dataStoredb = super.cftlib().getConnection(Database.Datastore);
             PreparedStatement statement = dataStoredb.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            if (rs.next()) {
                caseCount = rs.getInt(1);
            }
        }
        return caseCount;
    }

    @SneakyThrows
    private Boolean caseAppearsInSearch() {
        var request = buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
            "http://localhost:4452/data/internal/searchCases?ctid=NFD&page=1",
            HttpPost::new);
        var query = """
            {
              "native_es_query":{"from":0,"query":{"bool":{"must":[]}},"size":25,"sort":[{"_id": "desc"}]},
              "supplementary_data":["*"]
            }""";
        request.setEntity(new StringEntity(query, ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        var r = mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
        var aCase = (Map) ((List)r.get("cases")).get(0);
        var fields = (Map) aCase.get("fields");
        assertThat(fields.get("applicant1FirstName"), equalTo("app1_first_name"));;
        assertThat(fields.get("applicant2FirstName"), equalTo("app2_first_name"));;
        assertThat(((List)fields.get("notes")).size(), equalTo(4));;
        assertThat(fields.get("[LAST_MODIFIED_DATE]"), notNullValue());
        assertThat(fields.get("[LAST_STATE_MODIFIED_DATE]"), notNullValue());

        return true;
    }


    private void updateDueDate() throws Exception {

        var token = ccdApi.startEvent(
            getAuthorisation("TEST_CASE_WORKER_USER@mailinator.com"),
            getServiceAuth(), String.valueOf(caseRef), "caseworker-update-due-date").getToken();

        var body = Map.of(
            "data", Map.of(
                "dueDate", "2020-01-01"
            ),
            "event", Map.of(
                "id", "caseworker-update-due-date",
                "summary", "summary",
                "description", "description"
            ),
            "event_token", token,
            "ignore_warning", false
        );

        var e =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef + "/events", HttpPost::new);
        e.addHeader("experimental", "true");
        e.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-event.v2+json;charset=UTF-8");

        e.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(e);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(201));
    }


    private void addNote() throws Exception {

        var token = ccdApi.startEvent(
            getAuthorisation("TEST_CASE_WORKER_USER@mailinator.com"),
            getServiceAuth(), String.valueOf(caseRef), "caseworker-add-note").getToken();

        var body = Map.of(
            "data", Map.of(
                "note", "Test!"
            ),
            "event", Map.of(
                "id", "caseworker-add-note",
                "summary", "summary",
                "description", "description"
            ),
            "event_token", token,
            "ignore_warning", false
        );

        var e =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef + "/events", HttpPost::new);
        e.addHeader("experimental", "true");
        e.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-event.v2+json;charset=UTF-8");

        e.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(e);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(201));
    }

    private String getAuthorisation(String user) {
        return idam.getAccessToken(user, "");
    }

    private String getServiceAuth() {
        return cftlib().generateDummyS2SToken("ccd_gw");
    }

    <T extends HttpRequestBase> T buildRequest(String user, String url, Function<String, T> ctor) {
        var request = ctor.apply(url);
        var token = idam.getAccessToken(user, "");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("ServiceAuthorization", cftlib().generateDummyS2SToken("ccd_gw"));
        request.addHeader("Authorization",  token);
        return request;
    }

}
