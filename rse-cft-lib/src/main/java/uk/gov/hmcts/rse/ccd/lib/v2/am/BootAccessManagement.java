package uk.gov.hmcts.rse.ccd.lib.v2.am;

import com.microsoft.applicationinsights.TelemetryClient;
import feign.Feign;
import feign.jackson.JacksonEncoder;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.ServiceAuthTokenGenerator;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.roleassignment.RoleAssignmentApplication;
import uk.gov.hmcts.reform.roleassignment.config.SecurityConfiguration;
import uk.gov.hmcts.reform.roleassignment.config.SwaggerConfiguration;
import uk.gov.hmcts.reform.roleassignment.util.Swagger2SpringBoot;
import uk.gov.hmcts.rse.ccd.lib.YamlPropertySourceFactory;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackageClasses = RoleAssignmentApplication.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            RoleAssignmentApplication.class,
            SecurityConfiguration.class,
            SwaggerConfiguration.class,
            Swagger2SpringBoot.class
        }),
    }
)
@PropertySources({
    @PropertySource(
        factory = YamlPropertySourceFactory.class,
        value = {
            "classpath:am/application.yaml",
        }),
    @PropertySource("classpath:rse/application.properties"),
    @PropertySource("classpath:rse/am.properties")
})
@EntityScan(basePackages = {
    "uk.gov.hmcts.reform.roleassignment.data"
})
@EnableJpaRepositories(basePackages = {
    "uk.gov.hmcts.reform.roleassignment.data"
})
@EnableFeignClients(
    basePackageClasses = {
        IdamApi.class
        }
)
@EnableCaching
public class BootAccessManagement {

//  @Bean
//  public ServiceAuthorisationApi generateServiceAuthorisationApi(@Value("${idam.s2s-auth.url}") final String s2sUrl) {
//    return Feign.builder()
//        .encoder(new JacksonEncoder())
//        .contract(new SpringMvcContract())
//        .target(ServiceAuthorisationApi.class, s2sUrl);
//  }

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

//  @ConditionalOnMissingBean
//  @Bean
//  public TelemetryClient client() {
//    return new TelemetryClient();
//  }

}
