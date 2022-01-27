package uk.gov.hmcts.rse.ccd.lib.v2.lib;

import com.microsoft.applicationinsights.TelemetryClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGeneratorFactory;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.authorisation.validators.ServiceAuthTokenValidator;


@ComponentScan(basePackageClasses = ParentContextConfiguration.class)
@Configuration
public class ParentContextConfiguration {

  @ConditionalOnMissingBean
  @Bean
  public AuthTokenGenerator authTokenGenerator(
      @Value("${idam.s2s-auth.totp_secret}") final String secret,
      @Value("${idam.s2s-auth.microservice}") final String microService,
      final ServiceAuthorisationApi serviceAuthorisationApi
  ) {
    return AuthTokenGeneratorFactory.createDefaultGenerator(secret, microService, serviceAuthorisationApi);
  }

  @ConditionalOnMissingBean
  @Bean
  public AuthTokenValidator authTokenValidator(ServiceAuthorisationApi serviceAuthorisationApi) {
    return new ServiceAuthTokenValidator(serviceAuthorisationApi);
  }

  //     Because we disable CoreCaseDataApplication.class from scanning
//  @Bean
//  public Clock utcClock() {
//    return Clock.systemUTC();
//  }
//
  @ConditionalOnMissingBean
  @Bean
  public TelemetryClient defaultTelemetry() {
    return new TelemetryClient();
  }
//
}
