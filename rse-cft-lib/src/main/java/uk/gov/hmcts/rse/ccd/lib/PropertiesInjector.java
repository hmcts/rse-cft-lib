package uk.gov.hmcts.rse.ccd.lib;

import java.util.Properties;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

class PropertiesInjector implements
    ApplicationListener<ApplicationEnvironmentPreparedEvent> {

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    ConfigurableEnvironment environment = event.getEnvironment();
    Properties props = new Properties();
    environment.getPropertySources().addFirst(new PropertiesPropertySource("ccdLibProps", props));

    // We instead configure multiple package-scoped idam clients.
//    props.put("spring.autoconfigure.exclude",
//        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
//        + ",uk.gov.hmcts.reform.idam.client.IdamClient"
//    );

    // This may be set by definition store and requires unsetting or it will override data store's cache settings.
    props.put("spring.cache.cache-names", "");
    props.put("core_case_data.api.url", "http://localhost:${server.port}");
    props.put("ccd.user-profile.host", "http://localhost:${server.port}");
    props.put("ccd.case-definition.host", "http://localhost:${server.port}");
    props.put("ccd.ui-definition.host", "http://localhost:${server.port}");
    props.put("role.assignment.api.host", "http://localhost:${server.port}");
    // TODO - quick hack for a positive health check
    props.put("feign.client.config.datastoreclient.url", "https://idam-api.platform.hmcts.net");

    props.put("management.health.case-document-am-api.enabled", "false");
    props.put("flyway.locations", "/definition/db/migration");

//    props.put("spring.datasource.url", "jdbc:postgresql://localhost:6432/definition?stringtype=unspecified");
//    props.put("spring.datasource.username", "postgres");
//    props.put("spring.datasource.password", "postgres");

    props.put("search.elastic.hosts", "http://localhost:9200");
    props.put("search.elastic.data.hosts", "http://localhost:9200");
    props.put("elasticsearch.enabled", "true");
    props.put("elasticsearch.failImportIfError", "true");

    // Required by ccd
    props.put("spring.main.allow-circular-references", "true");
  }
}
