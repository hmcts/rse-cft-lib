package uk.gov.hmcts.rse.ccd.lib;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.ccd.definition.store.AppInsights;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;
import uk.gov.hmcts.ccd.definition.store.SecurityConfiguration;
import uk.gov.hmcts.ccd.definition.store.repository.AuthClientConfiguration;
import uk.gov.hmcts.reform.idam.client.OAuth2Configuration;

@Configuration
@ComponentScan(value = {
    "uk.gov.hmcts.rse.ccd.lib",
    "uk.gov.hmcts.ccd.definition"
}, excludeFilters = {
    // Def/Data transaction managers are identical
    @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd.*TransactionConfiguration\\.*"),
    // Unneeded caching
    @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd.*ApplicationConfiguration\\.*"),
    // Disable the default application component scanning or our excludes won't work.
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        CaseDataAPIApplication.class,
        AuthClientConfiguration.class,
        SecurityConfiguration.class,
        AppInsights.class
    }),
})
@EnableJpaRepositories(basePackages = "uk.gov.hmcts.ccd.definition")
@EntityScan(basePackages = "uk.gov.hmcts.ccd.definition")
public class CCDLibAutoConfigure {

  public CCDLibAutoConfigure() {

  }

  // Because we disabled def store ApplicationConfiguration
  @Bean
  RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
