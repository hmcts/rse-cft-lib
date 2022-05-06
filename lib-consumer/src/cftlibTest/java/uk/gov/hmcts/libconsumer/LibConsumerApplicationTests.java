package uk.gov.hmcts.libconsumer;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.rse.ccd.lib.Project;
import uk.gov.hmcts.rse.ccd.lib.test.CftlibTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibConsumerApplicationTests extends CftlibTest {

    @SneakyThrows
    @Test
    void testController() {
      var request = buildGet("http://localhost:8489/index");
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
    void listJurisdictions() {
        var request = buildGet("http://localhost:8489/aggregated/caseworkers/:uid/jurisdictions?access=read");
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
        var request = buildGet("http://localhost:8489/data/internal/case-types/NFD/work-basket-inputs");
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
      var request = buildGet("http://localhost:8489/data/caseworkers/:uid/jurisdictions/DIVORCE/case-types/NFD/cases/pagination_metadata");

      var response = HttpClientBuilder.create().build().execute(request);
      assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
    }

    // S2S tokens should be leasable by s2s simulator under two endpoints
    @SneakyThrows
    @Test
    void leaseS2SToken() {
      var request = buildRequest("http://localhost:8489/lease", HttpPost::new);
      request.setEntity(new StringEntity(new Gson().toJson(Map.of("microservice", "foo")), ContentType.APPLICATION_JSON));

      var response = HttpClientBuilder.create().build().execute(request);
      assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
      request = buildRequest("http://localhost:8489/testing-support/lease", HttpPost::new);
      request.setEntity(new StringEntity(new Gson().toJson(Map.of("microservice", "foo")), ContentType.APPLICATION_JSON));
      response = HttpClientBuilder.create().build().execute(request);
      assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
    }

    @Test
    void caseCreation() throws IOException {
        var request = buildGet("http://localhost:8489/data/internal/case-types/NFD/event-triggers/create-test-application?ignore-warning=false");
        request.addHeader("experimental", "true");
        request.addHeader("Accept", "application/vnd.uk.gov.hmcts.ccd-data-store-api.ui-start-case-trigger.v2+json;charset=UTF-8");

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

        var createCase = buildRequest("http://localhost:4452/data/case-types/NFD/cases?ignore-warning=false", HttpPost::new);
        createCase.addHeader("experimental", "true");
        createCase.addHeader("Accept", "application/vnd.uk.gov.hmcts.ccd-data-store-api.create-case.v2+json;charset=UTF-8");

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
        request.addHeader("ServiceAuthorization", generateDummyS2SToken("ccd_gw"));
        request.addHeader("Authorization", "Bearer " + buildJwt());
        return request;
    }

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

}
