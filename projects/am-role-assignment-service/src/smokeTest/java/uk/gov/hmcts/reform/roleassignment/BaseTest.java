package uk.gov.hmcts.reform.roleassignment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import feign.Feign;
import feign.jackson.JacksonEncoder;
import net.serenitybdd.junit.spring.integration.SpringIntegrationSerenityRunner;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.ServiceAuthTokenGenerator;
import uk.gov.hmcts.reform.idam.client.models.TokenRequest;
import uk.gov.hmcts.reform.idam.client.models.TokenResponse;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.BadRequestException;
import uk.gov.hmcts.reform.roleassignment.controller.advice.exception.ResourceNotFoundException;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

@RunWith(SpringIntegrationSerenityRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    RestTemplate restTemplate = new RestTemplate();
    protected static final ObjectMapper mapper = new ObjectMapper();

    @BeforeClass
    public static void init() {
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public ServiceAuthorisationApi generateServiceAuthorisationApi(final String s2sUrl) {
        return Feign.builder()
                    .encoder(new JacksonEncoder())
                    .contract(new SpringMvcContract())
                    .target(ServiceAuthorisationApi.class, s2sUrl);
    }

    public ServiceAuthTokenGenerator authTokenGenerator(
        final String secret,
        final String microService,
        final ServiceAuthorisationApi serviceAuthorisationApi) {
        return new ServiceAuthTokenGenerator(secret, microService, serviceAuthorisationApi);
    }


    public String searchUserByUserId(UserTokenProviderConfig config) {
        TokenRequest request = config.prepareTokenRequest();
        new ResponseEntity<>(HttpStatus.OK);
        ResponseEntity<TokenResponse> response;
        HttpHeaders headers = new HttpHeaders();
        try {
            String url = String.format(
                "%s/o/token?client_id=%s&client_secret=%s&grant_type=%s&scope=%s&username=%s&password=%s",
                config.getIdamURL(),
                request.getClientId(),
                config.getClientSecret(),
                request.getGrantType(),
                "openid+roles+profile+authorities",
                request.getUsername(),
                request.getPassword()
            );

            headers.setContentType(MediaType.parseMediaType(MediaType.APPLICATION_FORM_URLENCODED_VALUE));
            HttpEntity<?> entity = new HttpEntity<>(headers);
            response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                TokenResponse.class
            );

            if (HttpStatus.OK.equals(response.getStatusCode())) {
                log.info("Positive response");
                return Objects.requireNonNull(response.getBody()).accessToken;
            } else {
                log.error("There is some problem in fetching access token {}", response
                    .getStatusCode());
                throw new ResourceNotFoundException("Not Found");
            }
        } catch (HttpClientErrorException exception) {
            log.error("HttpClientErrorException {}", exception.getMessage());
            throw new BadRequestException("Unable to fetch access token");

        }
    }

    @TestConfiguration
    static class Configuration {
        Connection connection;

        @Bean
        public EmbeddedPostgres embeddedPostgres() throws IOException {
            return EmbeddedPostgres
                .builder()
                .start();
        }

        @Bean
        public DataSource dataSource() throws IOException, SQLException {
            final EmbeddedPostgres pg = embeddedPostgres();

            final Properties props = new Properties();
            // Instruct JDBC to accept JSON string for JSONB
            props.setProperty("stringtype", "unspecified");
            props.setProperty("user", "postgres");
            connection = DriverManager.getConnection(pg.getJdbcUrl("postgres"), props);
            return new SingleConnectionDataSource(connection, true);
        }

        @PreDestroy
        public void contextDestroyed() throws IOException, SQLException {
            if (connection != null) {
                connection.close();
            }
            embeddedPostgres().close();
        }
    }
}
