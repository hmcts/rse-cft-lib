package uk.gov.hmcts.divorce.cftlib;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lombok.SneakyThrows;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.divorce.common.event.CreateTestCase;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestWithCCD extends CftlibTest {

    @Autowired
    private IdamClient idam;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private CoreCaseDataApi ccdApi;

    @Order(1)
    @Test
    public void caseCreation() throws Exception {
        var token = ccdApi.startCase(getAuthorisation("TEST_SOLICITOR@mailinator.com"),
            getServiceAuth(),
            "NFD",
            "create-test-application").getToken();

        var body = Map.of(
            "data", Map.of(
                "applicationType", "soleApplication",
                "applicant1SolicitorRepresented", "No",
                "applicant2SolicitorRepresented", "No",
                // applicant2@gmail.com  =  6e508b49-1fa8-3d3c-8b53-ec466637315b
                "applicant2UserId", "6e508b49-1fa8-3d3c-8b53-ec466637315b",
                "stateToTransitionApplicationTo", "Holding"
            ),
            "event", Map.of(
                "id", "create-test-application",
                "summary", "",
                "description", ""
            ),
            "event_token", token,
            "ignore_warning", false
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
        assertThat(r.get("state"), equalTo("Holding"));

        // Check we can load the case
        var c = ccdApi.getCase(getAuthorisation("TEST_SOLICITOR@mailinator.com"), getServiceAuth(), String.valueOf(caseRef));
        assertThat(c.getState(), equalTo("Holding"));
        assertThat(CreateTestCase.submittedCallbackTriggered, equalTo(true));
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
            .timeout(Duration.ofSeconds(100))
            .ignoreExceptions()
            .until(this::caseAppearsInSearch);
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
