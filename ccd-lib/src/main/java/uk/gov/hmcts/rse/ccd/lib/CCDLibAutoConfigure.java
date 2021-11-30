package uk.gov.hmcts.rse.ccd.lib;

import com.microsoft.applicationinsights.TelemetryClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import uk.gov.hmcts.ccd.AliasWebConfig;
import uk.gov.hmcts.ccd.CoreCaseDataApplication;
import uk.gov.hmcts.ccd.UserProfileApplication;
import uk.gov.hmcts.ccd.data.AuthClientsConfiguration;
import uk.gov.hmcts.ccd.definition.store.CaseDataAPIApplication;
import uk.gov.hmcts.ccd.definition.store.repository.AuthClientConfiguration;
import uk.gov.hmcts.ccd.definition.store.security.JwtGrantedAuthoritiesConverter;
import uk.gov.hmcts.ccd.hikari.HikariConfigurationPropertiesReportEndpoint;

@Configuration
@AutoConfigureBefore({
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
    // Common ccd configs we wish to disable/substitute.
    @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd.*(Transaction|Security|Swagger)Configuration\\.*"),
    // Registers a duplicate rest template, package private in definition store.
    @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd.*ApplicationConfiguration\\.*"),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        // Definition store. Excluded to disable the default application component scanning or our excludes won't work.
        CaseDataAPIApplication.class,
        // Use the one from data store.
        JwtGrantedAuthoritiesConverter.class,
        AuthClientConfiguration.class,

        // Data store
        CoreCaseDataApplication.class,
        AuthClientsConfiguration.class,
        // Use the ones from def store
        AliasWebConfig.class,

        // User profile
        UserProfileApplication.class,
        HikariConfigurationPropertiesReportEndpoint.class
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
