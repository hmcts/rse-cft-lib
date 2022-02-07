package uk.gov.hmcts.rse.ccd.lib.impl;

import com.microsoft.applicationinsights.TelemetryClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import uk.gov.hmcts.ccd.CoreCaseDataApplication;
import uk.gov.hmcts.ccd.config.SwaggerConfiguration;
import uk.gov.hmcts.ccd.data.AuthClientsConfiguration;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGeneratorFactory;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.authorisation.validators.ServiceAuthTokenValidator;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataClientAutoConfiguration;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.rse.ccd.lib.injected.Common;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackageClasses = {
        CoreCaseDataApplication.class,
        Common.class
    },
    excludeFilters = {
        @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd\\.(definition|userprofile)\\..*"),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            uk.gov.hmcts.ccd.SecurityConfiguration.class,
            CoreCaseDataApplication.class,
            CoreCaseDataClientAutoConfiguration.class,
            SwaggerConfiguration.class,
            // We exclude this since it enables an unwanted S2S feign client
            AuthClientsConfiguration.class
        }),
    }
)
@EntityScan(basePackages = {
    "uk.gov.hmcts.ccd.data"
})
@EnableJpaRepositories(basePackages = {
    "uk.gov.hmcts.ccd.data"
})
@EnableFeignClients(
    clients = {
        IdamApi.class,
    })
public class BootData {

  @Bean
  public Clock utcClock() {
    return Clock.systemUTC();
  }

  @Bean
  public AuthTokenGenerator authTokenGenerator(
      @Value("${idam.s2s-auth.totp_secret}") final String secret,
      @Value("${idam.s2s-auth.microservice}") final String microService,
      final ServiceAuthorisationApi serviceAuthorisationApi
  ) {
    return AuthTokenGeneratorFactory.createDefaultGenerator(secret, microService, serviceAuthorisationApi);
  }

  @Bean
  public AuthTokenValidator authTokenValidator(ServiceAuthorisationApi serviceAuthorisationApi) {
    return new ServiceAuthTokenValidator(serviceAuthorisationApi);
  }

  // TODO: investigate flakey AI autoconfiguration
  @Primary
  @Bean
  public TelemetryClient defaultTelemetry() {
    return new TelemetryClient();
  }
}
