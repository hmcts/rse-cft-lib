package uk.gov.hmcts.reform.roleassignment;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.NoArgsConstructor;
import net.serenitybdd.junit.spring.integration.SpringIntegrationSerenityRunner;
import net.serenitybdd.rest.SerenityRest;
import net.thucydides.core.annotations.WithTag;
import net.thucydides.core.annotations.WithTags;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RunWith(SpringIntegrationSerenityRunner.class)
@NoArgsConstructor
@WithTags({@WithTag("testType:Smoke")})
public class SmokeTest extends BaseTest {

    public static final String AUTHORIZATION = "Authorization";
    public static final String SERVICE_AUTHORIZATION = "ServiceAuthorization";
    public static final String BEARER = "Bearer ";

    UserTokenProviderConfig config;
    String accessToken;
    String serviceAuth;

    @Value("${launchdarkly.sdk.environment}")
    private String environment;

    @Value("${launchdarkly.sdk.user}")
    private String userName;

    @Value("${launchdarkly.sdk.key}")
    private String sdkKey;

    @Before
    public void setUp() {
        config = new UserTokenProviderConfig();
        accessToken = searchUserByUserId(config);
        serviceAuth = authTokenGenerator(
            config.getSecret(),
            config.getMicroService(),
            generateServiceAuthorisationApi(config.getS2sUrl())
        ).generate();
    }

    @Rule
    public FeatureFlagToggleEvaluator featureFlagToggleEvaluator = new FeatureFlagToggleEvaluator(this);

    @Test
    public void should_receive_response_for_get_static_roles() {

        String targetInstance = config.getRoleAssignmentUrl() + "/am/role-assignments/roles";
        RestAssured.useRelaxedHTTPSValidation();

        Response response = SerenityRest
            .given()
            .relaxedHTTPSValidation()
            .header(SERVICE_AUTHORIZATION, BEARER + serviceAuth)
            .header(AUTHORIZATION, BEARER + accessToken)
            .when()
            .get(targetInstance)
            .andReturn();
        response.then().assertThat().statusCode(HttpStatus.OK.value());
    }

    @Test
    public void should_receive_response_for_get_by_actor_id() {

        var targetInstance = config.getRoleAssignmentUrl()
            + "/am/role-assignments/actors/0b00bfc0-bb00-00ea-b0de-0000ac000000";
        RestAssured.useRelaxedHTTPSValidation();

        Response response = SerenityRest
            .given()
            .relaxedHTTPSValidation()
            .header(SERVICE_AUTHORIZATION, BEARER + serviceAuth)
            .header(AUTHORIZATION, BEARER + accessToken)
            .when()
            .get(targetInstance)
            .andReturn();
        response.then().assertThat().statusCode(HttpStatus.OK.value());
    }

    @Test
    public void should_receive_response_for_delete_by_assignment_id() {

        var targetInstance = config.getRoleAssignmentUrl()
            + "/am/role-assignments/dbd4177f-94f6-4e91-bb9b-591faa81dfd5";
        RestAssured.useRelaxedHTTPSValidation();

        Response response = SerenityRest
            .given()
            .relaxedHTTPSValidation()
            .header("Content-Type", "application/json")
            .header(SERVICE_AUTHORIZATION, BEARER + serviceAuth)
            .header(AUTHORIZATION, BEARER + accessToken)
            .when()
            .delete(targetInstance)
            .andReturn();
        response.then().assertThat().statusCode(HttpStatus.NO_CONTENT.value());
    }

    @Test
    public void should_receive_response_for_delete_by_process_and_reference() {

        var targetInstance = config.getRoleAssignmentUrl() + "/am/role-assignments?process=p2&reference=r2";
        RestAssured.useRelaxedHTTPSValidation();

        Response response = SerenityRest
            .given()
            .relaxedHTTPSValidation()
            .header(SERVICE_AUTHORIZATION, BEARER + serviceAuth)
            .header(AUTHORIZATION, BEARER + accessToken)
            .when()
            .delete(targetInstance)
            .andReturn();
        response.then().assertThat().statusCode(HttpStatus.NO_CONTENT.value());
    }

    //@Test
    public void should_receive_response_for_add_role_assignment() throws IOException {

        var targetInstance = config.getRoleAssignmentUrl() + "/am/role-assignments";
        RestAssured.useRelaxedHTTPSValidation();

        InputStream input = SmokeTest.class.getClassLoader().getResourceAsStream("create_request_body.json");
        assert input != null;
        var requestBody = IOUtils.toString(input, StandardCharsets.UTF_8.name());

        Response response = SerenityRest
            .given()
            .relaxedHTTPSValidation()
            .header("Content-Type", "application/json")
            .header(SERVICE_AUTHORIZATION, BEARER + serviceAuth)
            .header(AUTHORIZATION, BEARER + accessToken)
            .body(requestBody)
            .when()
            .post(targetInstance)
            .andReturn();
        response.then().assertThat().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value());
        input.close();
    }

    public String getEnvironment() {
        return environment;
    }

    public String getUserName() {
        return userName;
    }

    public String getSdkKey() {
        return sdkKey;
    }
}
