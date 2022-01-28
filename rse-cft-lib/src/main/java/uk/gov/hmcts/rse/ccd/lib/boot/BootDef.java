package uk.gov.hmcts.rse.ccd.lib.boot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;
import uk.gov.hmcts.ccd.definition.store.SecurityConfiguration;
import uk.gov.hmcts.ccd.definition.store.SwaggerConfiguration;
import uk.gov.hmcts.ccd.definition.store.repository.AuthClientConfiguration;
import uk.gov.hmcts.ccd.userprofile.endpoint.userprofile.UserProfileEndpoint;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGeneratorFactory;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.rse.ccd.lib.YamlPropertySourceFactory;
import uk.gov.hmcts.rse.ccd.lib.injected.Common;

@ComponentScan(
    basePackageClasses = {
        CaseDataAPIApplication.class,
        Common.class
    },
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        CaseDataAPIApplication.class,
        SwaggerConfiguration.class,
        SecurityConfiguration.class,
        AuthClientConfiguration.class
    }))
@PropertySources({
    @PropertySource(value = {
        "classpath:cftlib-defstore-es.yml"
    }
        , factory = YamlPropertySourceFactory.class),
    @PropertySource("classpath:definitionstore/application.properties"),
    @PropertySource("classpath:rse/definitionstore.properties"),
    @PropertySource("classpath:rse/application.properties"),
})
@EntityScan(basePackages = {
    "uk.gov.hmcts.ccd.definition.store"
})
@EnableJpaRepositories(basePackages = {
    "uk.gov.hmcts.ccd.definition.store"
})
@EnableFeignClients(
    clients = {
        IdamApi.class,
    })
@SpringBootConfiguration
@EnableAutoConfiguration
public class BootDef {

  @Bean
  public AuthTokenGenerator authTokenGenerator(
      @Value("${idam.s2s-auth.totp_secret}") final String secret,
      @Value("${idam.s2s-auth.microservice}") final String microService,
      final ServiceAuthorisationApi serviceAuthorisationApi
  ) {
    return AuthTokenGeneratorFactory.createDefaultGenerator(secret, microService, serviceAuthorisationApi);
  }
}
