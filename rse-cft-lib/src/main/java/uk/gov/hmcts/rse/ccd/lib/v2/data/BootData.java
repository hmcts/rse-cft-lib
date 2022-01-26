package uk.gov.hmcts.rse.ccd.lib.v2.data;

import com.microsoft.applicationinsights.TelemetryClient;
import java.time.Clock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import uk.gov.hmcts.ccd.CoreCaseDataApplication;
import uk.gov.hmcts.ccd.config.SwaggerConfiguration;

@SpringBootConfiguration
@EnableAutoConfiguration
//@ComponentScan(excludeFilters = { @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
//    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class) })
@ComponentScan(
    basePackageClasses = CoreCaseDataApplication.class,
    excludeFilters = {
        @ComponentScan.Filter(type= FilterType.REGEX, pattern = "uk\\.gov\\.hmcts\\.ccd\\.(definition|userprofile)\\..*"),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            uk.gov.hmcts.ccd.SecurityConfiguration.class,
            CoreCaseDataApplication.class,
            SwaggerConfiguration.class,
        }),
    }
)
@PropertySources({
    @PropertySource("classpath:datastore/application.properties"),
    @PropertySource("classpath:rse/application.properties"),
    @PropertySource("classpath:rse/datastore.properties")
})
@EntityScan(basePackages = {
    "uk.gov.hmcts.ccd.data"
})
@EnableJpaRepositories(basePackages = {
    "uk.gov.hmcts.ccd.data"
})
//@EnableFeignClients(
//    clients = {
//        IdamApi.class,
//    })
public class BootData {

  @Bean
  public Clock utcClock() {
    return Clock.systemUTC();
  }

  @ConditionalOnMissingBean
  @Bean
  public TelemetryClient client() {
    return new TelemetryClient();
  }

}
