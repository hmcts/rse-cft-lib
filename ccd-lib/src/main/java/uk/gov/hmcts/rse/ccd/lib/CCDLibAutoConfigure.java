package uk.gov.hmcts.rse.ccd.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import uk.gov.hmcts.ccd.AliasWebConfig;
import uk.gov.hmcts.ccd.CoreCaseDataApplication;
import uk.gov.hmcts.ccd.UserProfileApplication;
import uk.gov.hmcts.ccd.auth.AuthCheckerConfiguration;
import uk.gov.hmcts.ccd.auth.AuthorizedConfiguration;
import uk.gov.hmcts.ccd.config.SwaggerConfiguration;
import uk.gov.hmcts.ccd.data.AuthClientsConfiguration;
import uk.gov.hmcts.ccd.definition.store.AppInsights;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;
import uk.gov.hmcts.ccd.definition.store.SecurityConfiguration;
import uk.gov.hmcts.ccd.definition.store.repository.AuthClientConfiguration;
import uk.gov.hmcts.ccd.definition.store.security.JwtGrantedAuthoritiesConverter;
import uk.gov.hmcts.ccd.hikari.HikariConfigurationPropertiesReportEndpoint;

@Configuration
@AutoConfigureBefore({
    // We register a non-primary ObjectMapper to stop Jackson doing so
    // and conflicting with the one data store registers.
    JacksonAutoConfiguration.class,
    // Register our JPA config before the spring Hibernate auto config
    // so our beans (EntityManager) stop spring creating its own.
    HibernateJpaAutoConfiguration.class
})
@ComponentScan(
    nameGenerator = BeanNamer.class,
    basePackages = {
    "uk.gov.hmcts.rse.ccd.lib",
    "uk.gov.hmcts.ccd"
}, excludeFilters = {
    // Def/Data transaction managers are identical
    @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd.*TransactionConfiguration\\.*"),
    // Registers a duplicate rest template
    @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd.*ApplicationConfiguration\\.*"),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        // Def store
        // Disable the default application component scanning or our excludes won't work.
        CaseDataAPIApplication.class,
        // Use the one from data store.
        JwtGrantedAuthoritiesConverter.class,
        AuthClientConfiguration.class,
        SecurityConfiguration.class,
        AppInsights.class,
        uk.gov.hmcts.ccd.definition.store.SwaggerConfiguration.class,

        // Data store
        CoreCaseDataApplication.class,
        SwaggerConfiguration.class,
        AuthClientsConfiguration.class,
        uk.gov.hmcts.ccd.SecurityConfiguration.class,
        // Use the ones from def store
        AliasWebConfig.class,

        // User profile
        UserProfileApplication.class,
        uk.gov.hmcts.ccd.auth.SecurityConfiguration.class,
        uk.gov.hmcts.ccd.SwaggerConfiguration.class,
        HikariConfigurationPropertiesReportEndpoint.class,
        AuthCheckerConfiguration.class,
        AuthorizedConfiguration.class
    }),
})
@EntityScan(basePackages = "uk.gov.hmcts.ccd")
@EnableJpaRepositories(basePackages = "uk.gov.hmcts.ccd")
public class CCDLibAutoConfigure {

  // Because we disable CoreCaseDataApplication.class from scanning
  @Bean
  public Clock utcClock() {
    return Clock.systemUTC();
  }

  @Bean
  public TelemetryClient client() {
    return new TelemetryClient();
  }

}
