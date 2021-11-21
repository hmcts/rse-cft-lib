package uk.gov.hmcts.rse.ccd.lib;

import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.ccd.AliasWebConfig;
import uk.gov.hmcts.ccd.CoreCaseDataApplication;
import uk.gov.hmcts.ccd.config.SwaggerConfiguration;
import uk.gov.hmcts.ccd.data.AuthClientsConfiguration;
import uk.gov.hmcts.ccd.definition.store.AppInsights;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;
import uk.gov.hmcts.ccd.definition.store.SecurityConfiguration;
import uk.gov.hmcts.ccd.definition.store.elastic.config.ElasticSearchConfiguration;
import uk.gov.hmcts.ccd.definition.store.repository.AuthClientConfiguration;
import uk.gov.hmcts.ccd.security.JwtGrantedAuthoritiesConverter;
import uk.gov.hmcts.ccd.security.idam.IdamRepository;
import uk.gov.hmcts.reform.idam.client.OAuth2Configuration;

@Configuration
@ComponentScan(
    nameGenerator = BeanNamer.class,
    value = {
    "uk.gov.hmcts.rse.ccd.lib",
    "uk.gov.hmcts.ccd"
}, excludeFilters = {
    // Def/Data transaction managers are identical
    @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd.*TransactionConfiguration\\.*"),
    // Unneeded caching
    @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd.*ApplicationConfiguration\\.*"),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        // Disable the default application component scanning or our excludes won't work.
        CaseDataAPIApplication.class,
        AuthClientConfiguration.class,
        SecurityConfiguration.class,
        AppInsights.class,
        uk.gov.hmcts.ccd.definition.store.SwaggerConfiguration.class,
        ElasticSearchConfiguration.class,

        // Data store
        CoreCaseDataApplication.class,
        SwaggerConfiguration.class,
        AuthClientsConfiguration.class,
        uk.gov.hmcts.ccd.SecurityConfiguration.class,
        // Use the ones from def store
        JwtGrantedAuthoritiesConverter.class,
        IdamRepository.class,
        AliasWebConfig.class
    }),
})
@EnableJpaRepositories(basePackages = "uk.gov.hmcts.ccd")
@EntityScan(basePackages = "uk.gov.hmcts.ccd")
public class CCDLibAutoConfigure {

  public CCDLibAutoConfigure() {

  }

  // Because we disable CoreCaseDataApplication.class from scanning
  @Bean
  public Clock utcClock() {
    return Clock.systemUTC();
  }
}
