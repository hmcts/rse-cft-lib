package uk.gov.hmcts.libconsumer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.TextCodec;
import lombok.SneakyThrows;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
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
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibConsumerApplicationTests extends CftlibTest {

    @Autowired
    MockMvc mockMvc;

    @SneakyThrows
    @Test
    void testController() {
        mockMvc.perform(get("/index"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(content().string(containsString("Hello world!")));
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
        var request = buildGet("http://localhost:4452/aggregated/caseworkers/banderous/jurisdictions?access=read");
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
        var request = buildGet("http://localhost:4452/data/internal/case-types/NFD/work-basket-inputs");
        request.addHeader("experimental", "true");

        var response = HttpClientBuilder.create().build().execute(request);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        var entity = EntityUtils.toString(response.getEntity());
        var m = new Gson().fromJson(entity, Map.class);
        List l = (List) m.get("workbasketInputs");

        assertThat(l.size(), greaterThan(0));
    }

    HttpGet buildGet(String url) {
        var request = new HttpGet(url);
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
