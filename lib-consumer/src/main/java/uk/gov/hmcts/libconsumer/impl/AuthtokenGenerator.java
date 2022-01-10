package uk.gov.hmcts.libconsumer.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGeneratorFactory;

@Configuration
public class AuthtokenGenerator {
  // A typical application AuthTokenGenerator.
  // The BeanIsolator should stop this getting injected into the protected
  // common component packages.
  @Bean
  public AuthTokenGenerator serviceAuthTokenGenerator(ServiceAuthorisationApi s2s) {
    return AuthTokenGeneratorFactory.createDefaultGenerator("AAAAAAAAAAAAAAAAA", "foo", s2s);
  }
}
