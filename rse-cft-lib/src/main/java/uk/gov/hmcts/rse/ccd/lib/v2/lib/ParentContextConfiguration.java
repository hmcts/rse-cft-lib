package uk.gov.hmcts.rse.ccd.lib.v2.lib;

import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@ComponentScan(basePackageClasses = ParentContextConfiguration.class)
@Configuration
public class ParentContextConfiguration {

  @ConditionalOnMissingBean
  @Bean
  public TelemetryClient defaultTelemetry() {
    return new TelemetryClient();
  }
}
