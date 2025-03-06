package uk.gov.hmcts.reform.roleassignment;

import feign.Feign;
import feign.jackson.JacksonEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.ServiceAuthTokenGenerator;
import uk.gov.hmcts.reform.idam.client.IdamApi;

import java.time.Clock;

@SpringBootApplication
@EnableTransactionManagement(proxyTargetClass = true)
@EnableCircuitBreaker
@EnableCaching
@EnableScheduling
@EnableRetry
@EnableFeignClients(basePackages = {
    "uk.gov.hmcts.reform.roleassignment"}, basePackageClasses = {IdamApi.class, ServiceAuthorisationApi.class})

public class RoleAssignmentApplication {


    public static void main(final String[] args) {
        SpringApplication.run(RoleAssignmentApplication.class);
    }

    @Bean
    public ServiceAuthorisationApi generateServiceAuthorisationApi(@Value("${idam.s2s-auth.url}") final String s2sUrl) {
        return Feign.builder()
            .encoder(new JacksonEncoder())
            .contract(new SpringMvcContract())
            .target(ServiceAuthorisationApi.class, s2sUrl);
    }

    @Bean
    public ServiceAuthTokenGenerator authTokenGenerator(
        @Value("${idam.s2s-auth.totp_secret}") final String secret,
        @Value("${idam.s2s-auth.microservice}") final String microService,
        final ServiceAuthorisationApi serviceAuthorisationApi) {
        return new ServiceAuthTokenGenerator(secret, microService, serviceAuthorisationApi);
    }

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }



}
