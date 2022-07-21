package uk.gov.hmcts.libconsumer;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.TextCodec;
import lombok.SneakyThrows;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibConsumerApplicationTests extends CftlibTest {

    public static String generateDummyS2SToken(String serviceName) {
        return Jwts.builder()
            .setSubject(serviceName)
            .setIssuedAt(new Date())
            .signWith(SignatureAlgorithm.HS256, TextCodec.BASE64.encode("AA"))
            .compact();
    }

    public static String buildJwt() {
        return JWT.create()
            .withSubject("banderous")
            .withNotBefore(new Date())
            .withIssuedAt(new Date())
            .withClaim("tokenName", "access_token")
            .withExpiresAt(Date.from(LocalDateTime.now().plusDays(100).toInstant(ZoneOffset.UTC)))
            .sign(Algorithm.HMAC256("a secret"));
    }

    @SneakyThrows
    @Test
    void testController() {
        var request = buildGet("http://localhost:7431/index");
        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
    }

    @SneakyThrows
    @Test
    void addressLookup() {
        var request = buildGet("http://localhost:4452/addresses");
        var response = HttpClientBuilder.create().build().execute(request);

        var entity = EntityUtils.toString(response.getEntity());
        var m = new Gson().fromJson(entity, Map.class);

        assertThat(m.containsKey("header"), is(true));
    }

    @SneakyThrows
    @Test
    void invalidS2STokenReturnsUnauthorised() {
        var request = buildGet("http://localhost:4452/addresses");
        request.removeHeaders("ServiceAuthorization");
        request.addHeader("ServiceAuthorization", "nonsense");
        var response = HttpClientBuilder.create().build().execute(request);

        assertThat(response.getStatusLine().getStatusCode(), is(401));
    }

    @SneakyThrows
    @Test
    void listJurisdictions() {
        var request = buildGet("http://localhost:7431/aggregated/caseworkers/:uid/jurisdictions?access=read");
        // Test xui talking direct to ccd without the gateway.
        // The s2s subject should be rewritten to ccd_gw by the lib.
        request.removeHeaders("ServiceAuthorization");
        request.addHeader("ServiceAuthorization", generateDummyS2SToken("xui_webapp"));
        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        var entity = EntityUtils.toString(response.getEntity());
        var list = new Gson().fromJson(entity, List.class);

        assertThat(list.size(), equalTo(1));
    }

    @SneakyThrows
    @Test
    void getWorkbasketInputs() {
        var request = buildGet("http://localhost:7431/data/internal/case-types/NFD/work-basket-inputs");
        request.addHeader("experimental", "true");

        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        var entity = EntityUtils.toString(response.getEntity());
        var m = new Gson().fromJson(entity, Map.class);
        List l = (List) m.get("workbasketInputs");

        assertThat(l.size(), greaterThan(0));
    }

    @SneakyThrows
    @Test
    void getPaginationMetadata() {
        var request = buildGet(
            "http://localhost:7431/data/caseworkers/:uid/jurisdictions/DIVORCE/case-types/NFD/cases/pagination_metadata");

        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
    }

    // S2S tokens should be leasable by s2s simulator under two endpoints
    @SneakyThrows
    @Test
    void leaseS2SToken() {
        var request = buildRequest("http://localhost:7431/lease", HttpPost::new);
        request.setEntity(
            new StringEntity(new Gson().toJson(Map.of("microservice", "foo")), ContentType.APPLICATION_JSON));

        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        request = buildRequest("http://localhost:7431/testing-support/lease", HttpPost::new);
        request.setEntity(
            new StringEntity(new Gson().toJson(Map.of("microservice", "foo")), ContentType.APPLICATION_JSON));
        response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
    }

    @Order(1)
    @Test
    void caseCreation() throws IOException {
        var request = buildGet(
            "http://localhost:7431/data/internal/case-types/NFD/event-triggers/create-test-application?ignore-warning=false");
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

    @Order(2)
    @SneakyThrows
    @Test
    void searchCases() {
        // Give logstash some time to index the case created by the previous test
        Thread.sleep(3000);
        await()
            .timeout(Duration.ofSeconds(20))
            .until(this::caseAppearsInSearch);
    }

    @SneakyThrows
    private Boolean caseAppearsInSearch() {
        var request = buildRequest(
            "http://localhost:4452/data/internal/searchCases?ctid=NFD&use_case=WORKBASKET&view=WORKBASKET&page=1",
            HttpPost::new);
        var query =
            "{\"native_es_query\":{\"from\":0,\"query\":{\"bool\":{\"must\":[]}},\"size\":25,\"sort\":[]},"
                + "\"supplementary_data\":[\"*\"]}";
        request.setEntity(new StringEntity(query, ContentType.APPLICATION_JSON));
        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        var total = (int) Double.parseDouble(new Gson().fromJson(EntityUtils.toString(response.getEntity()), Map.class)
            .get("total").toString());
        return total > 0;
    }

    HttpGet buildGet(String url) {
        return buildRequest(url, HttpGet::new);
    }

    <T extends HttpRequestBase> T buildRequest(String url, Function<String, T> ctor) {
        var request = ctor.apply(url);
        request.addHeader("Content-Type", "application/json");
        request.addHeader("ServiceAuthorization", generateDummyS2SToken("ccd_gw"));
        request.addHeader("Authorization", "Bearer " + buildJwt());
        return request;
    }

}
