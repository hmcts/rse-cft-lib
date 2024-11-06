package uk.gov.hmcts.divorce.cftlib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

import java.util.Map;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestWithCCD extends CftlibTest {

    @Autowired
    private IdamClient idam;

    @Autowired
    private ObjectMapper mapper;

    @Order(1)
    @Test
    public void caseCreation() throws Exception {
        var request = buildGet("TEST_SOLICITOR@mailinator.com",
            "http://localhost:4452/data/internal/case-types/NFD/event-triggers/create-test-application?ignore-warning=false");
        request.addHeader("experimental", "true");
        request.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.ui-start-case-trigger.v2+json;charset=UTF-8");

        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        var token = new Gson().fromJson(EntityUtils.toString(response.getEntity()), Map.class)
            .get("event_token");

        var body = Map.of(
            "data", Map.of(
                "applicationType", "soleApplication",
                "applicant1SolicitorRepresented", "No",
                "applicant2SolicitorRepresented", "No",
                "applicant2UserId", "93b108b7-4b26-41bf-ae8f-6e356efb11b3",
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
        response = HttpClientBuilder.create().build().execute(createCase);
        var r = new Gson().fromJson(EntityUtils.toString(response.getEntity()), Map.class);
        caseRef = Long.parseLong((String) r.get("id"));
        assertThat(response.getStatusLine().getStatusCode(), equalTo(201));

    }

    private long caseRef;
    @Order(2)
    @Test
    public void addNote() throws Exception {
        var request = buildGet("TEST_CASE_WORKER_USER@mailinator.com",
            "http://localhost:4452/cases/" + caseRef + "/event-triggers/caseworker-add-note");
        request.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.start-event-trigger.v2+json;charset=UTF-8");

        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        var token = new Gson().fromJson(EntityUtils.toString(response.getEntity()), Map.class)
            .get("token");

        var body = Map.of(
            "event_data", Map.of(
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

        var createCase =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef + "/events", HttpPost::new);
        createCase.addHeader("experimental", "true");
        createCase.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-event.v2+json;charset=UTF-8");

        createCase.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        response = HttpClientBuilder.create().build().execute(createCase);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(201));


        var get =
            buildRequest("TEST_CASE_WORKER_USER@mailinator.com",
                "http://localhost:4452/cases/" + caseRef, HttpGet::new);
        get.addHeader("experimental", "true");
        get.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.case.v2+json;charset=UTF-8");

        response = HttpClientBuilder.create().build().execute(get);
        var result = mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        var data = (Map) result.get("data");
        var caseData = mapper.readValue(mapper.writeValueAsString(data), CaseData.class);
        assertThat(caseData.getNotes().size(), equalTo(1));

    }

    HttpGet buildGet(String user, String url) {
        return buildRequest(user, url, HttpGet::new);
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
