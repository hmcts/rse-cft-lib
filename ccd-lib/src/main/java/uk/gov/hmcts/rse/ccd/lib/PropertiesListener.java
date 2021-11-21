package uk.gov.hmcts.rse.ccd.lib;

import java.util.Properties;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

public class PropertiesListener implements
    ApplicationListener<ApplicationEnvironmentPreparedEvent> {
  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    ConfigurableEnvironment environment = event.getEnvironment();
    Properties props = new Properties();
    props.put("ccd.user-profile.host", "http://localhost");
    props.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration");
    environment.getPropertySources().addFirst(new PropertiesPropertySource("ccdLibProps", props));
  }
}
