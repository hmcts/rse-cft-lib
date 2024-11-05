package uk.gov.hmcts.divorce.cftlib;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

import java.util.Map;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestWithCCD extends CftlibTest {

    @Test
    public void bootsWithCCD() throws Exception {
        var request = buildGet(
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
                "stateToTransitionApplicationTo", "Draft"
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
            buildRequest("http://localhost:4452/data/case-types/NFD/cases?ignore-warning=false", HttpPost::new);
        createCase.addHeader("experimental", "true");
        createCase.addHeader("Accept",
            "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-case.v2+json;charset=UTF-8");

        createCase.setEntity(new StringEntity(new Gson().toJson(body), ContentType.APPLICATION_JSON));
        response = HttpClientBuilder.create().build().execute(createCase);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(201));

    }

    HttpGet buildGet(String url) {
        return buildRequest(url, HttpGet::new);
    }

    <T extends HttpRequestBase> T buildRequest(String url, Function<String, T> ctor) {
        var request = ctor.apply(url);
        request.addHeader("Content-Type", "application/json");
        request.addHeader("ServiceAuthorization", cftlib().generateDummyS2SToken("ccd_gw"));
        request.addHeader("Authorization", "Bearer " + cftlib().buildJwt());
        return request;
    }

}
